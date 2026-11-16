package br.com.ecosense.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;

import br.com.ecosense.config.EcoSenseProperties;
import br.com.ecosense.config.EcoSenseProperties.DeviceConfig;
import br.com.ecosense.config.EcoSenseProperties.Conversion;
import br.com.ecosense.config.EcoSenseProperties.Fields;
import br.com.ecosense.config.EcoSenseProperties.StorageConfig;
import br.com.ecosense.thingspeak.Feed;
import br.com.ecosense.thingspeak.ThingSpeakClient;

@Service
public class ReadingService {

    public static final int HISTORY_DAYS = 31;
    static final int MAX_PARALLEL_REQUESTS = 6;
    private static final Logger log = LoggerFactory.getLogger(ReadingService.class);

    private final ThingSpeakClient client;
    private final EcoSenseProperties props;
    private final Clock clock;
    private final Map<String, Cached<DeviceData>> deviceCache = new ConcurrentHashMap<>();
    private final Map<String, Cached<Double>> storageCache = new ConcurrentHashMap<>();
    private final Map<String, List<Reading>> hourlyStore = new ConcurrentHashMap<>();
    private final Map<String, ReentrantLock> locks = new ConcurrentHashMap<>();
    private final ExecutorService requests = Executors.newFixedThreadPool(MAX_PARALLEL_REQUESTS, daemonThreads());

    public ReadingService(ThingSpeakClient client, EcoSenseProperties props, Clock clock) {
        this.client = client;
        this.props = props;
        this.clock = clock;
        logStartup();
    }

    private void logStartup() {
        if (props.thingspeak().simulate()) {
            log.info("Modo simulado: leituras geradas localmente; o ThingSpeak não é chamado.");
            return;
        }
        String key = props.thingspeak().readApiKey();
        log.info("ThingSpeak: Read API Key {}.",
                key == null || key.isBlank()
                        ? "NÃO configurada (só funciona com canais públicos; defina THINGSPEAK_READ_API_KEY)"
                        : "configurada (" + key.length() + " caracteres)");
    }

    private static java.util.concurrent.ThreadFactory daemonThreads() {
        AtomicInteger n = new AtomicInteger();
        return r -> {
            Thread t = new Thread(r, "thingspeak-" + n.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
    }

    @PreDestroy
    void shutdown() {
        requests.shutdownNow();
    }

    public void warmUp(List<DeviceConfig> devices) {
        List<Thread> tasks = new ArrayList<>();
        for (DeviceConfig d : devices) tasks.add(Thread.ofVirtual().start(() -> data(d)));
        for (StorageConfig s : props.storage()) tasks.add(Thread.ofVirtual().start(() -> storagePct(s)));
        for (Thread t : tasks) {
            try {
                t.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    public void evict(String deviceId) {
        ReentrantLock lock = lockFor(deviceId);
        lock.lock();
        try {
            deviceCache.remove(deviceId);
            hourlyStore.remove(deviceId);
        } finally {
            lock.unlock();
        }
    }

    private ReentrantLock lockFor(String key) {
        return locks.computeIfAbsent(key, k -> new ReentrantLock());
    }

    private boolean fresh(Cached<?> cached, Instant now) {
        return cached != null && cached.fetchedAt().plus(props.cacheTtl()).isAfter(now);
    }

    public record DeviceData(Reading latest, List<Reading> hourly) {
        static final DeviceData EMPTY = new DeviceData(null, List.of());
    }

    private record Cached<T>(Instant fetchedAt, T value) {}

    public DeviceData data(DeviceConfig d) {
        Cached<DeviceData> cached = deviceCache.get(d.id());
        if (fresh(cached, clock.instant())) return cached.value();
        if (d.channelId() == null) return DeviceData.EMPTY;
        ReentrantLock lock = lockFor(d.id());
        lock.lock();
        try {
            Instant now = clock.instant();
            cached = deviceCache.get(d.id());
            if (fresh(cached, now)) return cached.value();
            return fetch(d, cached, now);
        } finally {
            lock.unlock();
        }
    }

    private DeviceData fetch(DeviceConfig d, Cached<DeviceData> cached, Instant now) {
        try {
            String key = keyFor(d.readApiKey());
            Future<List<Feed>> last = requests.submit(() -> client.latest(d.channelId(), key, 1));
            List<Reading> hourly = hourly(d, key, now);
            List<Feed> latest = await(last);
            DeviceData data = new DeviceData(
                    latest.isEmpty() ? null : toReading(latest.get(latest.size() - 1), d.fields(), d.conversion()),
                    hourly);
            deviceCache.put(d.id(), new Cached<>(now, data));
            return data;
        } catch (RuntimeException e) {
            warnFailure(d.channelId(), d.id(), e);
            DeviceData fallback = cached != null ? cached.value() : DeviceData.EMPTY;
            deviceCache.put(d.id(), new Cached<>(now, fallback));
            return fallback;
        }
    }

    private List<Reading> hourly(DeviceConfig d, String key, Instant now) {
        Instant from = now.minus(Duration.ofDays(HISTORY_DAYS));
        List<Reading> stored = hourlyStore.getOrDefault(d.id(), List.of());
        Map<Instant, Reading> byHour = new TreeMap<>();
        Instant fetchFrom;
        if (stored.isEmpty()) {
            fetchFrom = from;
        } else {
            fetchFrom = stored.get(stored.size() - 1).at().truncatedTo(ChronoUnit.HOURS).minus(Duration.ofHours(1));
            for (Reading r : stored) {
                if (!r.at().isBefore(from) && r.at().isBefore(fetchFrom)) byHour.put(hourOf(r), r);
            }
        }
        Duration chunk = Duration.ofDays(d.historyChunkDays());
        List<Future<List<Feed>>> parts = new ArrayList<>();
        for (Instant start = fetchFrom; start.isBefore(now); start = start.plus(chunk)) {
            Instant s = start, end = start.plus(chunk).isAfter(now) ? now : start.plus(chunk);
            parts.add(requests.submit(() -> client.averaged(d.channelId(), key, s, end, 60)));
        }
        try {
            for (Future<List<Feed>> part : parts) {
                for (Feed f : await(part)) {
                    Reading r = toReading(f, d.fields(), d.conversion());
                    byHour.put(hourOf(r), r);
                }
            }
        } catch (RuntimeException e) {
            parts.forEach(p -> p.cancel(true));
            throw e;
        }
        List<Reading> merged = List.copyOf(byHour.values());
        hourlyStore.put(d.id(), merged);
        return merged;
    }

    private static <T> T await(Future<T> future) {
        try {
            return future.get();
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException re) throw re;
            throw new IllegalStateException(e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Busca interrompida.", e);
        }
    }

    private <T> T request(Callable<T> call) {
        return await(requests.submit(call));
    }

    private static Instant hourOf(Reading r) {
        return r.at().truncatedTo(ChronoUnit.HOURS);
    }

    private static void warnFailure(Long channelId, String name, RuntimeException e) {
        String message = String.valueOf(e.getMessage());
        String hint = message.contains("\"-1\"")
                ? " — canal privado sem Read API Key válida: defina THINGSPEAK_READ_API_KEY (ou torne o canal público)."
                : "";
        log.warn("Falha ao ler o canal {} ({}) no ThingSpeak: {}{}", channelId, name, message, hint);
    }

    public Double storagePct(StorageConfig s) {
        Cached<Double> cached = storageCache.get(s.name());
        if (fresh(cached, clock.instant())) return cached.value();
        if (s.channelId() == null) return null;
        ReentrantLock lock = lockFor("storage:" + s.name());
        lock.lock();
        try {
            Instant now = clock.instant();
            cached = storageCache.get(s.name());
            if (fresh(cached, now)) return cached.value();
            return fetchStorage(s, cached, now);
        } finally {
            lock.unlock();
        }
    }

    private Double fetchStorage(StorageConfig s, Cached<Double> cached, Instant now) {
        try {
            List<Feed> last = request(() -> client.latest(s.channelId(), keyFor(s.readApiKey()), 1));
            Double pct = last.isEmpty() ? null : s.conversion().battery(last.get(last.size() - 1).number(s.field()));
            storageCache.put(s.name(), new Cached<>(now, pct));
            return pct;
        } catch (RuntimeException e) {
            warnFailure(s.channelId(), s.name(), e);
            Double fallback = cached != null ? cached.value() : null;
            storageCache.put(s.name(), new Cached<>(now, fallback));
            return fallback;
        }
    }

    private String keyFor(String specific) {
        return specific != null && !specific.isBlank() ? specific : props.thingspeak().readApiKey();
    }

    static Reading toReading(Feed f, Fields m, Conversion c) {
        return new Reading(f.createdAt(),
                c.battery(f.number(m.battery())),
                c.consumption(f.number(m.consumption())),
                c.temperature(f.number(m.temperature())),
                c.harvested(f.number(m.harvested())));
    }
}
