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

/**
 * Busca e guarda em cache as leituras dos canais do ThingSpeak.
 * Por dispositivo são 2 chamadas: a última entrada (estado atual) e as médias por hora dos
 * últimos {@value #HISTORY_DAYS} dias (histórico, tendência e previsão).
 * Se o ThingSpeak falhar, usa o último resultado em cache (ou nenhum dado) em vez de derrubar a API.
 *
 * Desempenho na primeira carga (até ~16 pedidos por canal):
 * - os pedidos ao ThingSpeak rodam em paralelo, no máximo {@value #MAX_PARALLEL_REQUESTS} ao mesmo tempo;
 * - cada dispositivo é buscado por uma requisição de cada vez: quem chega enquanto a busca está em
 *   andamento espera e usa o mesmo resultado, em vez de repetir os pedidos;
 * - {@link #warmUp} carrega tudo assim que o back-end liga ({@link ReadingWarmUp}).
 */
@Service
public class ReadingService {

    public static final int HISTORY_DAYS = 31;
    /** pedidos simultâneos ao ThingSpeak (serviço gratuito: sem exagerar) */
    static final int MAX_PARALLEL_REQUESTS = 6;
    private static final Logger log = LoggerFactory.getLogger(ReadingService.class);

    private final ThingSpeakClient client;
    private final EcoSenseProperties props;
    private final Clock clock;
    private final Map<String, Cached<DeviceData>> deviceCache = new ConcurrentHashMap<>();
    private final Map<String, Cached<Double>> storageCache = new ConcurrentHashMap<>();
    /** médias horárias já baixadas, por dispositivo (para a busca incremental) */
    private final Map<String, List<Reading>> hourlyStore = new ConcurrentHashMap<>();
    /** uma busca por dispositivo (ou bateria) de cada vez */
    private final Map<String, ReentrantLock> locks = new ConcurrentHashMap<>();
    /**
     * Executa SÓ os pedidos ao ThingSpeak (tarefas que não criam outras tarefas): o tamanho fixo limita os
     * pedidos simultâneos sem risco de uma tarefa ficar esperando outra na fila.
     */
    private final ExecutorService requests = Executors.newFixedThreadPool(MAX_PARALLEL_REQUESTS, daemonThreads());

    public ReadingService(ThingSpeakClient client, EcoSenseProperties props, Clock clock) {
        this.client = client;
        this.props = props;
        this.clock = clock;
        logStartup();
    }

    /** Diz no log de onde vêm os dados e se a Read API Key foi encontrada (sem mostrar a chave). */
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

    /**
     * Carrega as leituras de todos os dispositivos e baterias do sistema, em paralelo, e espera terminar.
     * Usado ao ligar o back-end, para a primeira tela já encontrar os dados em cache.
     */
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

    /** Descarta as leituras guardadas de um dispositivo (após editar o canal ou removê-lo). */
    public void evict(String deviceId) {
        /* espera uma busca em andamento terminar, para ela não gravar de volta dados do canal antigo */
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

    /** latest = última entrada (null se o canal nunca transmitiu); hourly = médias por hora, em ordem. */
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
            /* outra requisição pode ter acabado de buscar enquanto esta esperava */
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
            /* guarda também a falha: evita repetir a chamada a cada requisição até o cache vencer */
            DeviceData fallback = cached != null ? cached.value() : DeviceData.EMPTY;
            deviceCache.put(d.id(), new Cached<>(now, fallback));
            return fallback;
        }
    }

    /**
     * Médias horárias dos últimos {@value #HISTORY_DAYS} dias.
     * O ThingSpeak lê no máximo 8000 entradas por requisição ANTES de calcular as médias; em canais que
     * enviam a cada poucos segundos isso cobre só alguns dias. Por isso:
     * - na primeira vez, busca em pedaços de {@code history-chunk-days} dias;
     * - nas seguintes, busca só as últimas horas e junta ao que já tinha (incremental).
     */
    private List<Reading> hourly(DeviceConfig d, String key, Instant now) {
        Instant from = now.minus(Duration.ofDays(HISTORY_DAYS));
        List<Reading> stored = hourlyStore.getOrDefault(d.id(), List.of());
        Map<Instant, Reading> byHour = new TreeMap<>();
        Instant fetchFrom;
        if (stored.isEmpty()) {
            fetchFrom = from;
        } else {
            /* refaz as 2 últimas horas (a hora corrente ainda está mudando) */
            fetchFrom = stored.get(stored.size() - 1).at().truncatedTo(ChronoUnit.HOURS).minus(Duration.ofHours(1));
            for (Reading r : stored) {
                if (!r.at().isBefore(from) && r.at().isBefore(fetchFrom)) byHour.put(hourOf(r), r);
            }
        }
        /* os pedaços são independentes: busca em paralelo e junta por hora (a ordem não importa) */
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
            parts.forEach(p -> p.cancel(true)); // um pedaço falhou: não adianta esperar os outros
            throw e;
        }
        List<Reading> merged = List.copyOf(byHour.values());
        hourlyStore.put(d.id(), merged);
        return merged;
    }

    /** Espera o resultado de um pedido; a falha volta como a exceção original (tratada em {@link #data}). */
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

    /** O ThingSpeak responde "-1" quando o canal é privado e a Read API Key está ausente ou errada. */
    private static void warnFailure(Long channelId, String name, RuntimeException e) {
        String message = String.valueOf(e.getMessage());
        String hint = message.contains("\"-1\"")
                ? " — canal privado sem Read API Key válida: defina THINGSPEAK_READ_API_KEY (ou torne o canal público)."
                : "";
        log.warn("Falha ao ler o canal {} ({}) no ThingSpeak: {}{}", channelId, name, message, hint);
    }

    /** Percentual de carga de uma bateria do sistema (null se indisponível). */
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

    /** Lê os fields mapeados e converte para as unidades do EcoSense (%, W, °C). */
    static Reading toReading(Feed f, Fields m, Conversion c) {
        return new Reading(f.createdAt(),
                c.battery(f.number(m.battery())),
                c.consumption(f.number(m.consumption())),
                c.temperature(f.number(m.temperature())),
                c.harvested(f.number(m.harvested())));
    }
}
