package br.com.ecosense.config;

import java.time.Duration;
import java.time.ZoneId;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ecosense")
public record EcoSenseProperties(
        ThingSpeak thingspeak,
        ZoneId zone,
        Duration cacheTtl,
        Duration offlineAfter,
        List<String> corsOrigins,
        List<DeviceConfig> devices,
        List<StorageConfig> storage) {

    public EcoSenseProperties {
        thingspeak = thingspeak != null ? thingspeak : new ThingSpeak(null, null, false);
        zone = zone != null ? zone : ZoneId.of("America/Sao_Paulo");
        cacheTtl = cacheTtl != null ? cacheTtl : Duration.ofSeconds(60);
        offlineAfter = offlineAfter != null ? offlineAfter : Duration.ofMinutes(30);
        corsOrigins = corsOrigins != null ? corsOrigins : List.of();
        devices = devices != null ? devices : List.of();
        storage = storage != null ? storage : List.of();
    }

    public record ThingSpeak(String baseUrl, String readApiKey, boolean simulate) {
        public ThingSpeak {
            baseUrl = baseUrl != null && !baseUrl.isBlank() ? baseUrl : "https://api.thingspeak.com";
        }
    }

    public record DeviceConfig(
            String id, String name, String location, String region,
            Double latitude, Double longitude,
            Integer txInterval, String type, String priority,
            String harvestingType, Double sunHours,
            Long channelId, String readApiKey, Integer historyChunkDays,
            Fields fields, Conversion conversion) {

        public DeviceConfig {
            historyChunkDays = historyChunkDays != null && historyChunkDays > 0 ? historyChunkDays : 31;
            txInterval = txInterval != null ? txInterval : 5;
            type = type != null ? type : "Sensor";
            priority = priority != null ? priority : "Média";
            fields = fields != null ? fields : new Fields(null, null, null, null);
            conversion = conversion != null ? conversion : Conversion.NONE;
        }
    }

    public record Fields(String battery, String consumption, String temperature, String harvested) {
        public Fields {
            battery = battery != null ? battery : "field1";
            consumption = consumption != null ? consumption : "field2";
            temperature = temperature != null ? temperature : "field3";
            harvested = harvested != null ? harvested : "field4";
        }

        public boolean measuresConsumption() {
            return !"none".equalsIgnoreCase(consumption);
        }

        public boolean measuresHarvested() {
            return !"none".equalsIgnoreCase(harvested);
        }
    }

    public record StorageConfig(
            String name, String type, Double capacityKwh,
            Long channelId, String readApiKey, String field, Conversion conversion) {

        public StorageConfig {
            type = type != null ? type : "Bateria";
            capacityKwh = capacityKwh != null ? capacityKwh : 0d;
            field = field != null ? field : "field1";
            conversion = conversion != null ? conversion : Conversion.NONE;
        }
    }

    public record Conversion(Double batteryVoltageMin, Double batteryVoltageMax,
                             Double consumptionScale, Double harvestedScale, Double temperatureScale) {

        public static final Conversion NONE = new Conversion(null, null, null, null, null);

        public Conversion {
            consumptionScale = consumptionScale != null ? consumptionScale : 1d;
            harvestedScale = harvestedScale != null ? harvestedScale : 1d;
            temperatureScale = temperatureScale != null ? temperatureScale : 1d;
        }

        public Double battery(Double raw) {
            if (raw == null) return null;
            if (batteryVoltageMin == null || batteryVoltageMax == null || batteryVoltageMax <= batteryVoltageMin) return raw;
            double pct = (raw - batteryVoltageMin) / (batteryVoltageMax - batteryVoltageMin) * 100;
            return Math.max(0, Math.min(100, pct));
        }

        public Double consumption(Double raw) {
            return raw == null ? null : Math.max(0, raw * consumptionScale);
        }

        public Double harvested(Double raw) {
            return raw == null ? null : Math.max(0, raw * harvestedScale);
        }

        public Double temperature(Double raw) {
            return raw == null ? null : raw * temperatureScale;
        }
    }
}
