package br.com.ecosense.thingspeak;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import br.com.ecosense.config.EcoSenseProperties;
import br.com.ecosense.config.EcoSenseProperties.DeviceConfig;
import br.com.ecosense.config.EcoSenseProperties.Simulation;
import br.com.ecosense.config.EcoSenseProperties.StorageConfig;

/**
 * Gera leituras no mesmo formato do ThingSpeak, a partir dos parâmetros {@code sim} de cada
 * dispositivo. Permite desenvolver e testar sem canais reais. Ativo com
 * {@code ecosense.thingspeak.simulate=true}. Os valores são determinísticos (mesma hora = mesmo valor).
 */
@Component
@ConditionalOnProperty(name = "ecosense.thingspeak.simulate", havingValue = "true")
public class SimulatedThingSpeakClient implements ThingSpeakClient {

    private final EcoSenseProperties props;
    private final Clock clock;

    public SimulatedThingSpeakClient(EcoSenseProperties props, Clock clock) {
        this.props = props;
        this.clock = clock;
    }

    @Override
    public List<Feed> latest(long channelId, String readApiKey, int results) {
        Instant now = clock.instant();
        Instant last = lastTransmission(channelId, now);
        List<Feed> feeds = new ArrayList<>();
        for (int i = results - 1; i >= 0; i--) {
            feeds.add(feedAt(channelId, last.minus(Duration.ofMinutes(5L * i)), now));
        }
        return feeds;
    }

    @Override
    public List<Feed> averaged(long channelId, String readApiKey, Instant start, Instant end, int averageMinutes) {
        Instant now = clock.instant();
        Instant last = lastTransmission(channelId, now);
        Duration step = Duration.ofMinutes(averageMinutes);
        List<Feed> feeds = new ArrayList<>();
        for (Instant t = start; !t.isAfter(end) && !t.isAfter(last); t = t.plus(step)) {
            feeds.add(feedAt(channelId, t, now));
        }
        return feeds;
    }

    /** Dispositivos "offline" na simulação param de transmitir há {@code offlineMinutes}. */
    private Instant lastTransmission(long channelId, Instant now) {
        Integer offline = simulation(channelId).offlineMinutes();
        return offline == null ? now.minusSeconds(30) : now.minus(Duration.ofMinutes(offline));
    }

    private Feed feedAt(long channelId, Instant t, Instant now) {
        Optional<DeviceConfig> device = props.devices().stream()
                .filter(d -> d.channelId() != null && d.channelId() == channelId).findFirst();
        if (device.isPresent()) return deviceFeed(device.get(), t, now);
        StorageConfig storage = props.storage().stream()
                .filter(s -> s.channelId() != null && s.channelId() == channelId).findFirst().orElse(null);
        double battery = storage == null ? 50 : storage.sim().battery();
        String field = storage == null ? "field1" : storage.field();
        return new Feed(t, Map.of(field, fmt(battery + noise(channelId, t, 1))));
    }

    private Feed deviceFeed(DeviceConfig d, Instant t, Instant now) {
        Simulation s = d.sim();
        long channel = d.channelId();
        double daysFromNow = Duration.between(now, t).toMinutes() / 1440.0;
        int hour = ZonedDateTime.ofInstant(t, props.zone()).getHour();

        double battery = clamp(s.battery() + s.trendPerDay() * daysFromNow + noise(channel, t, 0.8), 0, 100);
        double consumption = Math.max(0, s.consumptionW() * (1 + 0.12 * Math.sin((hour + channel) * 0.55)
                + noise(channel, t, 0.04)));
        /* solar segue o sol (0 à noite); multiplicar por PI mantém a média diária = harvestedW */
        double harvestFactor = "Solar".equalsIgnoreCase(d.harvestingType())
                ? Math.max(0, Math.sin((hour - 6) / 12.0 * Math.PI)) * Math.PI
                : 1 + noise(channel, t, 0.1);
        double harvested = d.harvestingType() == null ? 0 : Math.max(0, s.harvestedW() * harvestFactor);
        double temperature = s.temperature() + 3 * Math.sin((hour - 9) / 24.0 * 2 * Math.PI);

        var f = d.fields();
        return new Feed(t, Map.of(
                f.battery(), fmt(battery),
                f.consumption(), fmt(consumption),
                f.temperature(), fmt(temperature),
                f.harvested(), fmt(harvested)));
    }

    private Simulation simulation(long channelId) {
        return props.devices().stream()
                .filter(d -> d.channelId() != null && d.channelId() == channelId)
                .map(DeviceConfig::sim).findFirst()
                .orElseGet(() -> props.storage().stream()
                        .filter(s -> s.channelId() != null && s.channelId() == channelId)
                        .map(StorageConfig::sim).findFirst()
                        .orElse(new Simulation(null, null, null, null, null, null)));
    }

    /** Ruído determinístico em [-amplitude, +amplitude], fixo para cada canal e hora. */
    private static double noise(long channel, Instant t, double amplitude) {
        long hourIndex = t.getEpochSecond() / 3600;
        double x = Math.sin(channel * 12.9898 + hourIndex * 78.233) * 43758.5453;
        return (x - Math.floor(x) - 0.5) * 2 * amplitude;
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private static String fmt(double v) {
        return String.format(java.util.Locale.ROOT, "%.3f", v);
    }
}
