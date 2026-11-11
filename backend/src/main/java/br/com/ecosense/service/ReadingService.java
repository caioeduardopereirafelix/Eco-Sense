package br.com.ecosense.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

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
 */
@Service
public class ReadingService {

    public static final int HISTORY_DAYS = 31;
    private static final Logger log = LoggerFactory.getLogger(ReadingService.class);

    private final ThingSpeakClient client;
    private final EcoSenseProperties props;
    private final Clock clock;
    private final Map<String, Cached<DeviceData>> deviceCache = new ConcurrentHashMap<>();
    private final Map<String, Cached<Double>> storageCache = new ConcurrentHashMap<>();
    /** médias horárias já baixadas, por dispositivo (para a busca incremental) */
    private final Map<String, List<Reading>> hourlyStore = new ConcurrentHashMap<>();

    public ReadingService(ThingSpeakClient client, EcoSenseProperties props, Clock clock) {
        this.client = client;
        this.props = props;
        this.clock = clock;
        logStartup();
    }

    /** Diz no log de onde vêm os dados e se a Read API Key foi encontrada (sem mostrar a chave). */
    private void logStartup() {
        if (props.thingspeak().simulate()) {
            log.info("Modo simulado: leituras geradas localmente para {} dispositivo(s); o ThingSpeak não é chamado.",
                    props.devices().size());
            return;
        }
        String key = props.thingspeak().readApiKey();
        log.info("ThingSpeak: {} dispositivo(s). Read API Key {}.", props.devices().size(),
                key == null || key.isBlank()
                        ? "NÃO configurada (só funciona com canais públicos; defina THINGSPEAK_READ_API_KEY)"
                        : "configurada (" + key.length() + " caracteres)");
    }

    /** latest = última entrada (null se o canal nunca transmitiu); hourly = médias por hora, em ordem. */
    public record DeviceData(Reading latest, List<Reading> hourly) {
        static final DeviceData EMPTY = new DeviceData(null, List.of());
    }

    private record Cached<T>(Instant fetchedAt, T value) {}

    public DeviceData data(DeviceConfig d) {
        Instant now = clock.instant();
        Cached<DeviceData> cached = deviceCache.get(d.id());
        if (cached != null && cached.fetchedAt().plus(props.cacheTtl()).isAfter(now)) return cached.value();
        if (d.channelId() == null) return DeviceData.EMPTY;
        try {
            String key = keyFor(d.readApiKey());
            List<Feed> last = client.latest(d.channelId(), key, 1);
            DeviceData data = new DeviceData(
                    last.isEmpty() ? null : toReading(last.get(last.size() - 1), d.fields(), d.conversion()),
                    hourly(d, key, now));
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
        Duration chunk = Duration.ofDays(d.historyChunkDays());
        for (Instant start = fetchFrom; start.isBefore(now); start = start.plus(chunk)) {
            Instant end = start.plus(chunk).isAfter(now) ? now : start.plus(chunk);
            for (Feed f : client.averaged(d.channelId(), key, start, end, 60)) {
                Reading r = toReading(f, d.fields(), d.conversion());
                byHour.put(hourOf(r), r);
            }
        }
        List<Reading> merged = List.copyOf(byHour.values());
        hourlyStore.put(d.id(), merged);
        return merged;
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
        Instant now = clock.instant();
        Cached<Double> cached = storageCache.get(s.name());
        if (cached != null && cached.fetchedAt().plus(props.cacheTtl()).isAfter(now)) return cached.value();
        if (s.channelId() == null) return null;
        try {
            List<Feed> last = client.latest(s.channelId(), keyFor(s.readApiKey()), 1);
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
