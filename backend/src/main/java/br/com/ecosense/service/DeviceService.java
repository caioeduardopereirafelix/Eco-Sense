package br.com.ecosense.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.stereotype.Service;

import br.com.ecosense.api.ApiDtos.Device;
import br.com.ecosense.api.ApiDtos.Harvesting;
import br.com.ecosense.api.ApiException;
import br.com.ecosense.config.EcoSenseProperties;
import br.com.ecosense.config.EcoSenseProperties.DeviceConfig;
import br.com.ecosense.service.ReadingService.DeviceData;

/** Monta o estado atual de cada dispositivo: cadastro (configuração) + última leitura do ThingSpeak. */
@Service
public class DeviceService {

    private final ReadingService readings;
    private final EcoSenseProperties props;
    private final Clock clock;

    public DeviceService(ReadingService readings, EcoSenseProperties props, Clock clock) {
        this.readings = readings;
        this.props = props;
        this.clock = clock;
    }

    public List<DeviceConfig> configs() {
        return props.devices();
    }

    public DeviceConfig config(String id) {
        return props.devices().stream().filter(d -> d.id().equals(id)).findFirst()
                .orElseThrow(() -> ApiException.notFound("Dispositivo " + id + " não encontrado."));
    }

    public List<Device> devices() {
        return configs().stream().map(this::toDevice).toList();
    }

    public Device device(String id) {
        return toDevice(config(id));
    }

    /** Online = transmitiu dentro do limite (o maior entre offlineAfter e 3× o intervalo de transmissão). */
    public boolean isOnline(DeviceConfig d, DeviceData data) {
        if (data.latest() == null) return false;
        Duration limit = Duration.ofMinutes(3L * d.txInterval());
        if (props.offlineAfter().compareTo(limit) > 0) limit = props.offlineAfter();
        return data.latest().at().isAfter(clock.instant().minus(limit));
    }

    public Device toDevice(DeviceConfig d) {
        DeviceData data = readings.data(d);
        Reading latest = data.latest();
        boolean online = isOnline(d, data);
        List<Reading> last24h = since(data.hourly(), clock.instant().minus(Duration.ofHours(24)));
        Double avgConsumptionW = Stats.mean(last24h, Reading::consumptionW);
        Double avgHarvestedW = Stats.mean(last24h, Reading::harvestedW);

        int battery = latest != null && latest.battery() != null ? (int) Math.round(latest.battery()) : 0;
        double consumptionWhDay = online && avgConsumptionW != null ? Stats.sig(avgConsumptionW * 24) : 0;
        Double temperature = online && latest.temperature() != null ? Stats.round(latest.temperature(), 1) : null;
        Harvesting harvesting = d.harvestingType() == null ? null : new Harvesting(d.harvestingType(),
                online && avgHarvestedW != null ? Stats.sig(avgHarvestedW * 24) : 0);

        return new Device(d.id(), d.name(), d.location(), d.region(), d.latitude(), d.longitude(),
                online ? "Online" : "Offline", battery, consumptionWhDay,
                latest == null ? null : latest.at().truncatedTo(ChronoUnit.SECONDS),
                temperature, d.txInterval(), harvesting);
    }

    static List<Reading> since(List<Reading> readings, Instant from) {
        return readings.stream().filter(r -> !r.at().isBefore(from)).toList();
    }
}
