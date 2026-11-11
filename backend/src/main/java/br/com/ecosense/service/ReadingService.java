package br.com.ecosense.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import br.com.ecosense.config.EcoSenseProperties;
import br.com.ecosense.config.EcoSenseProperties.DeviceConfig;
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
            List<Feed> hourly = client.averaged(d.channelId(), key, now.minus(Duration.ofDays(HISTORY_DAYS)), now, 60);
            DeviceData data = new DeviceData(
                    last.isEmpty() ? null : toReading(last.get(last.size() - 1), d.fields()),
                    hourly.stream().map(f -> toReading(f, d.fields())).toList());
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
            Double pct = last.isEmpty() ? null : last.get(last.size() - 1).number(s.field());
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

    static Reading toReading(Feed f, Fields m) {
        return new Reading(f.createdAt(), f.number(m.battery()), f.number(m.consumption()),
                f.number(m.temperature()), f.number(m.harvested()));
    }
}
