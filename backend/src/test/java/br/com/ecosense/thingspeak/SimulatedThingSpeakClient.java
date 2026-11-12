package br.com.ecosense.thingspeak;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import br.com.ecosense.config.EcoSenseProperties;
import br.com.ecosense.config.EcoSenseProperties.DeviceConfig;
import br.com.ecosense.config.EcoSenseProperties.StorageConfig;
import br.com.ecosense.service.DeviceCatalog;

/**
 * SÓ PARA TESTES: gera leituras no mesmo formato do ThingSpeak, para os testes automáticos rodarem
 * sem internet e com dados previsíveis. Ativo com {@code ecosense.thingspeak.simulate=true}
 * (perfil "test", src/test/resources/application-test.yml). Cada canal se comporta conforme
 * {@code test-sim.channels.<channelId>}. Os valores são determinísticos (mesma hora = mesmo valor).
 */
@Component
@ConditionalOnProperty(name = "ecosense.thingspeak.simulate", havingValue = "true")
@EnableConfigurationProperties(SimulatedThingSpeakClient.SimulationProperties.class)
public class SimulatedThingSpeakClient implements ThingSpeakClient {

    /**
     * Comportamento de um canal simulado.
     *
     * @param battery        % de bateria agora
     * @param trendPerDay    variação da bateria em pontos percentuais por dia
     * @param consumptionW   consumo médio em W
     * @param harvestedW     coleta média em W (média do dia inteiro)
     * @param temperature    temperatura média em °C
     * @param offlineMinutes há quantos minutos parou de transmitir (null = online)
     */
    public record Sim(Double battery, Double trendPerDay, Double consumptionW,
                      Double harvestedW, Double temperature, Integer offlineMinutes) {
        static final Sim DEFAULT = new Sim(null, null, null, null, null, null);

        public Sim {
            battery = battery != null ? battery : 70d;
            trendPerDay = trendPerDay != null ? trendPerDay : 0d;
            consumptionW = consumptionW != null ? consumptionW : 0.5;
            harvestedW = harvestedW != null ? harvestedW : 0d;
            temperature = temperature != null ? temperature : 25d;
        }
    }

    /** Parâmetros dos canais simulados (prefixo {@code test-sim}). */
    @ConfigurationProperties(prefix = "test-sim")
    public record SimulationProperties(Map<Long, Sim> channels) {
        public SimulationProperties {
            channels = channels != null ? channels : Map.of();
        }
    }

    private final EcoSenseProperties props;
    private final SimulationProperties sims;
    private final Clock clock;
    /* lazy: o catálogo depende (indiretamente) deste cliente */
    private final ObjectProvider<DeviceCatalog> catalog;

    public SimulatedThingSpeakClient(EcoSenseProperties props, SimulationProperties sims, Clock clock,
                                     ObjectProvider<DeviceCatalog> catalog) {
        this.props = props;
        this.sims = sims;
        this.clock = clock;
        this.catalog = catalog;
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

    /** Canal simulado: nomes de fields padrão; nome e local do dispositivo que usa o canal, se houver. */
    @Override
    public ChannelMeta channel(long channelId, String readApiKey) {
        Optional<DeviceConfig> device = deviceFor(channelId);
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("field1", "Bateria (%)");
        fields.put("field2", "Consumo (W)");
        fields.put("field3", "Temperatura (°C)");
        fields.put("field4", "Energia coletada (W)");
        return new ChannelMeta(channelId,
                device.map(DeviceConfig::name).orElse("Canal simulado " + channelId),
                "Canal simulado (testes)",
                device.map(DeviceConfig::latitude).orElse(null),
                device.map(DeviceConfig::longitude).orElse(null),
                fields, lastTransmission(channelId, clock.instant()));
    }

    /** Canais "offline" param de transmitir há {@code offlineMinutes}. */
    private Instant lastTransmission(long channelId, Instant now) {
        Integer offline = sim(channelId).offlineMinutes();
        return offline == null ? now.minusSeconds(30) : now.minus(Duration.ofMinutes(offline));
    }

    private Sim sim(long channelId) {
        return sims.channels().getOrDefault(channelId, Sim.DEFAULT);
    }

    private Optional<DeviceConfig> deviceFor(long channelId) {
        return catalog.getObject().all().stream()
                .filter(d -> d.channelId() != null && d.channelId() == channelId).findFirst();
    }

    private Feed feedAt(long channelId, Instant t, Instant now) {
        Optional<DeviceConfig> device = deviceFor(channelId);
        if (device.isPresent()) return deviceFeed(device.get(), t, now);
        StorageConfig storage = props.storage().stream()
                .filter(s -> s.channelId() != null && s.channelId() == channelId).findFirst().orElse(null);
        String field = storage == null ? "field1" : storage.field();
        return new Feed(t, Map.of(field, fmt(sim(channelId).battery() + noise(channelId, t, 1))));
    }

    private Feed deviceFeed(DeviceConfig d, Instant t, Instant now) {
        Sim s = sim(d.channelId());
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
        Map<String, String> values = new LinkedHashMap<>();
        values.put(f.battery(), fmt(battery));
        values.put(f.consumption(), fmt(consumption));
        values.put(f.temperature(), fmt(temperature));
        values.put(f.harvested(), fmt(harvested));
        return new Feed(t, values);
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
