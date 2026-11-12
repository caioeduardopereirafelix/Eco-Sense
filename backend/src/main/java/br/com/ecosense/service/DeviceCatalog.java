package br.com.ecosense.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.ecosense.api.ApiDtos.ConversionSettings;
import br.com.ecosense.api.ApiDtos.DeviceSettings;
import br.com.ecosense.api.ApiDtos.FieldMapping;
import br.com.ecosense.api.ApiException;
import br.com.ecosense.config.EcoSenseProperties;
import br.com.ecosense.config.EcoSenseProperties.Conversion;
import br.com.ecosense.config.EcoSenseProperties.DeviceConfig;
import br.com.ecosense.config.EcoSenseProperties.Fields;
import br.com.ecosense.persistence.DeviceEntity;
import br.com.ecosense.persistence.DeviceRepository;

/**
 * Cadastro de dispositivos guardado no banco (tabela devices).
 * - Na primeira vez (tabela vazia), copia os dispositivos iniciais do application*.yml.
 * - Mantém uma cópia em memória para não consultar o banco a cada requisição; ela é recarregada
 *   a cada criação, edição ou remoção.
 */
@Service
public class DeviceCatalog {

    private static final Logger log = LoggerFactory.getLogger(DeviceCatalog.class);

    private final DeviceRepository repository;
    private final ReadingService readings;
    private final EcoSenseProperties props;
    private final Clock clock;
    private volatile List<DeviceConfig> cache;

    public DeviceCatalog(DeviceRepository repository, ReadingService readings, EcoSenseProperties props, Clock clock) {
        this.repository = repository;
        this.readings = readings;
        this.props = props;
        this.clock = clock;
    }

    /* ---------------- leitura ---------------- */

    public List<DeviceConfig> all() {
        List<DeviceConfig> current = cache;
        return current != null ? current : reload();
    }

    public Optional<DeviceConfig> find(String id) {
        return all().stream().filter(d -> d.id().equals(id)).findFirst();
    }

    public DeviceSettings settings(String id) {
        return toSettings(entity(id));
    }

    /* ---------------- escrita ---------------- */

    @Transactional
    public synchronized DeviceConfig create(DeviceSettings s) {
        if (s.id() == null || s.id().isBlank()) throw ApiException.badRequest("Informe o ID do dispositivo.");
        if (repository.existsById(s.id()))
            throw new ApiException(HttpStatus.CONFLICT, "Já existe um dispositivo com o ID " + s.id() + ".");
        requireChannel(s);
        DeviceEntity e = new DeviceEntity();
        e.setId(s.id());
        e.setPosition(repository.maxPosition() + 1);
        e.setCreatedAt(clock.instant());
        apply(s, e);
        repository.save(e);
        reload();
        return find(s.id()).orElseThrow();
    }

    @Transactional
    public synchronized DeviceConfig update(String id, DeviceSettings s) {
        if (s.id() != null && !s.id().isBlank() && !s.id().equals(id))
            throw ApiException.badRequest("O ID do dispositivo não pode ser alterado.");
        DeviceEntity e = entity(id);
        requireChannel(s);
        apply(s, e);
        repository.save(e);
        readings.evict(id); // canal ou conversão podem ter mudado
        reload();
        return find(id).orElseThrow();
    }

    @Transactional
    public synchronized void delete(String id) {
        repository.delete(entity(id));
        readings.evict(id);
        reload();
    }

    /* ---------------- carga e conversão ---------------- */

    private synchronized List<DeviceConfig> reload() {
        if (repository.count() == 0 && !props.devices().isEmpty()) seed();
        cache = repository.findAllByOrderByPositionAscIdAsc().stream().map(DeviceCatalog::toConfig).toList();
        return cache;
    }

    private void seed() {
        Instant now = clock.instant();
        int position = 1;
        for (DeviceConfig d : props.devices()) {
            DeviceEntity e = fromConfig(d);
            e.setPosition(position++);
            e.setCreatedAt(now);
            e.setUpdatedAt(now);
            repository.save(e);
        }
        log.info("Banco vazio: {} dispositivo(s) iniciais copiados da configuração.", props.devices().size());
    }

    private DeviceEntity entity(String id) {
        return repository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Dispositivo " + id + " não encontrado."));
    }

    private void requireChannel(DeviceSettings s) {
        if (!props.thingspeak().simulate() && s.channelId() == null)
            throw ApiException.badRequest("Informe o Channel ID do ThingSpeak.");
        ConversionSettings c = s.conversion();
        if (c != null && c.batteryVoltageMin() != null && c.batteryVoltageMax() != null
                && c.batteryVoltageMax() <= c.batteryVoltageMin())
            throw ApiException.badRequest("A tensão de bateria cheia deve ser maior que a de bateria vazia.");
    }

    /** Copia os campos editáveis do formulário para a entidade. */
    private void apply(DeviceSettings s, DeviceEntity e) {
        e.setName(s.name().trim());
        e.setLocation(blankToNull(s.location()));
        e.setRegion(blankToNull(s.region()));
        e.setLatitude(s.latitude());
        e.setLongitude(s.longitude());
        e.setTxInterval(s.txInterval());
        e.setType(blankToNull(s.type()));
        e.setPriority(blankToNull(s.priority()));
        e.setHarvestingType(blankToNull(s.harvestingType()));
        e.setSunHours(s.sunHours());
        e.setChannelId(s.channelId());
        e.setHistoryChunkDays(s.historyChunkDays());
        FieldMapping f = s.fields() != null ? s.fields() : new FieldMapping(null, null, null, null);
        Fields defaults = new Fields(f.battery(), f.consumption(), f.temperature(), f.harvested());
        e.setFieldBattery(defaults.battery());
        e.setFieldConsumption(defaults.consumption());
        e.setFieldTemperature(defaults.temperature());
        e.setFieldHarvested(defaults.harvested());
        ConversionSettings c = s.conversion() != null ? s.conversion() : new ConversionSettings(null, null, null, null, null);
        e.setBatteryVoltageMin(c.batteryVoltageMin());
        e.setBatteryVoltageMax(c.batteryVoltageMax());
        e.setConsumptionScale(c.consumptionScale());
        e.setHarvestedScale(c.harvestedScale());
        e.setTemperatureScale(c.temperatureScale());
        e.setUpdatedAt(clock.instant());
    }

    static DeviceConfig toConfig(DeviceEntity e) {
        return new DeviceConfig(e.getId(), e.getName(), e.getLocation(), e.getRegion(),
                e.getLatitude(), e.getLongitude(), e.getTxInterval(), e.getType(), e.getPriority(),
                e.getHarvestingType(), e.getSunHours(), e.getChannelId(), null, e.getHistoryChunkDays(),
                new Fields(e.getFieldBattery(), e.getFieldConsumption(), e.getFieldTemperature(), e.getFieldHarvested()),
                new Conversion(e.getBatteryVoltageMin(), e.getBatteryVoltageMax(),
                        e.getConsumptionScale(), e.getHarvestedScale(), e.getTemperatureScale()));
    }

    static DeviceEntity fromConfig(DeviceConfig d) {
        DeviceEntity e = new DeviceEntity();
        e.setId(d.id());
        e.setName(d.name());
        e.setLocation(d.location());
        e.setRegion(d.region());
        e.setLatitude(d.latitude());
        e.setLongitude(d.longitude());
        e.setTxInterval(d.txInterval());
        e.setType(d.type());
        e.setPriority(d.priority());
        e.setHarvestingType(d.harvestingType());
        e.setSunHours(d.sunHours());
        e.setChannelId(d.channelId());
        e.setHistoryChunkDays(d.historyChunkDays());
        e.setFieldBattery(d.fields().battery());
        e.setFieldConsumption(d.fields().consumption());
        e.setFieldTemperature(d.fields().temperature());
        e.setFieldHarvested(d.fields().harvested());
        Conversion c = d.conversion();
        e.setBatteryVoltageMin(c.batteryVoltageMin());
        e.setBatteryVoltageMax(c.batteryVoltageMax());
        e.setConsumptionScale(c.consumptionScale());
        e.setHarvestedScale(c.harvestedScale());
        e.setTemperatureScale(c.temperatureScale());
        return e;
    }

    static DeviceSettings toSettings(DeviceEntity e) {
        return new DeviceSettings(e.getId(), e.getName(), e.getLocation(), e.getRegion(),
                e.getLatitude(), e.getLongitude(), e.getTxInterval(), e.getType(), e.getPriority(),
                e.getHarvestingType(), e.getSunHours(), e.getChannelId(), e.getHistoryChunkDays(),
                new FieldMapping(e.getFieldBattery(), e.getFieldConsumption(), e.getFieldTemperature(), e.getFieldHarvested()),
                new ConversionSettings(e.getBatteryVoltageMin(), e.getBatteryVoltageMax(),
                        e.getConsumptionScale(), e.getHarvestedScale(), e.getTemperatureScale()));
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
