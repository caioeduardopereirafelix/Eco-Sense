package br.com.ecosense.config;

import java.time.Duration;
import java.time.ZoneId;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuração do EcoSense (prefixo {@code ecosense} no application.yml).
 * Cada dispositivo aponta para um canal do ThingSpeak e diz em qual field está cada leitura.
 */
@ConfigurationProperties(prefix = "ecosense")
public record EcoSenseProperties(
        ThingSpeak thingspeak,
        /* fuso usado para "hoje", horas e dias do histórico */
        ZoneId zone,
        /* por quanto tempo reaproveitar as leituras antes de consultar o ThingSpeak de novo */
        Duration cacheTtl,
        /* sem dados há mais que isso (ou 3× o intervalo de transmissão, o que for maior) = Offline */
        Duration offlineAfter,
        List<String> corsOrigins,
        List<DeviceConfig> devices,
        /* baterias/armazenamento do sistema (opcional) */
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

    /**
     * @param baseUrl    URL da API do ThingSpeak
     * @param readApiKey Read API Key padrão (use variável de ambiente; vazio para canais públicos)
     * @param simulate   true = não chama o ThingSpeak e gera leituras simuladas (desenvolvimento)
     */
    public record ThingSpeak(String baseUrl, String readApiKey, boolean simulate) {
        public ThingSpeak {
            baseUrl = baseUrl != null && !baseUrl.isBlank() ? baseUrl : "https://api.thingspeak.com";
        }
    }

    /**
     * Um dispositivo IoT. Dados cadastrais vêm daqui; leituras vêm do canal do ThingSpeak.
     *
     * @param txInterval     intervalo de transmissão em minutos
     * @param type           tipo de carga (Sensor, Comunicação, Vídeo…)
     * @param priority       Alta | Média | Baixa
     * @param harvestingType Solar | Térmico | Cinético… (null = não coleta energia)
     * @param sunHours       horas de sol por dia no local (null se não usa solar)
     * @param readApiKey     sobrescreve a chave padrão para este canal
     * @param historyChunkDays tamanho (dias) de cada pedido do histórico na primeira carga. O ThingSpeak
     *                       lê no máximo 8000 entradas por pedido: use ~2 para canais que enviam a cada
     *                       30 s, ~27 para 5 min. Padrão 31 (um pedido só).
     * @param conversion     converte as unidades do canal para as do EcoSense (ex.: V → %, mW → W)
     * @param sim            parâmetros do modo simulado (ignorado com o ThingSpeak real)
     */
    public record DeviceConfig(
            String id, String name, String location, String region,
            Double latitude, Double longitude,
            Integer txInterval, String type, String priority,
            String harvestingType, Double sunHours,
            Long channelId, String readApiKey, Integer historyChunkDays,
            Fields fields, Conversion conversion, Simulation sim) {

        public DeviceConfig {
            historyChunkDays = historyChunkDays != null && historyChunkDays > 0 ? historyChunkDays : 31;
            txInterval = txInterval != null ? txInterval : 5;
            type = type != null ? type : "Sensor";
            priority = priority != null ? priority : "Média";
            fields = fields != null ? fields : new Fields(null, null, null, null);
            conversion = conversion != null ? conversion : Conversion.NONE;
            sim = sim != null ? sim : Simulation.DEFAULT;
        }
    }

    /**
     * Em qual field do canal está cada leitura. Unidades esperadas no ThingSpeak (ou após a
     * {@link Conversion}): battery em %, consumption em W, temperature em °C, harvested em W.
     * Use {@code none} para uma leitura que o canal não tem.
     */
    public record Fields(String battery, String consumption, String temperature, String harvested) {
        public Fields {
            battery = battery != null ? battery : "field1";
            consumption = consumption != null ? consumption : "field2";
            temperature = temperature != null ? temperature : "field3";
            harvested = harvested != null ? harvested : "field4";
        }
    }

    /**
     * Bateria/armazenamento do sistema: lê a carga de um field de um canal
     * (em %, ou em volts com {@code conversion.battery-voltage-min/max}).
     */
    public record StorageConfig(
            String name, String type, Double capacityKwh,
            Long channelId, String readApiKey, String field, Conversion conversion, Simulation sim) {

        public StorageConfig {
            type = type != null ? type : "Bateria";
            capacityKwh = capacityKwh != null ? capacityKwh : 0d;
            field = field != null ? field : "field1";
            conversion = conversion != null ? conversion : Conversion.NONE;
            sim = sim != null ? sim : Simulation.DEFAULT;
        }
    }

    /**
     * Conversão das leituras do canal para as unidades do EcoSense.
     *
     * @param batteryVoltageMin tensão da bateria vazia (V). Com min e max definidos, o field de
     *                          bateria é lido como tensão e convertido linearmente para %
     * @param batteryVoltageMax tensão da bateria cheia (V)
     * @param consumptionScale  multiplica o consumo (ex.: 0.001 para mW → W; negativo inverte o sinal)
     * @param harvestedScale    multiplica a energia coletada (ex.: 0.001 para mW → W)
     * @param temperatureScale  multiplica a temperatura
     */
    public record Conversion(Double batteryVoltageMin, Double batteryVoltageMax,
                             Double consumptionScale, Double harvestedScale, Double temperatureScale) {

        public static final Conversion NONE = new Conversion(null, null, null, null, null);

        public Conversion {
            consumptionScale = consumptionScale != null ? consumptionScale : 1d;
            harvestedScale = harvestedScale != null ? harvestedScale : 1d;
            temperatureScale = temperatureScale != null ? temperatureScale : 1d;
        }

        /** % de bateria: tensão convertida (limitada a 0–100) ou o valor como veio. */
        public Double battery(Double raw) {
            if (raw == null) return null;
            if (batteryVoltageMin == null || batteryVoltageMax == null || batteryVoltageMax <= batteryVoltageMin) return raw;
            double pct = (raw - batteryVoltageMin) / (batteryVoltageMax - batteryVoltageMin) * 100;
            return Math.max(0, Math.min(100, pct));
        }

        /** Potência nunca é negativa: valores abaixo de zero viram 0. */
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

    /**
     * Comportamento do dispositivo no modo simulado.
     *
     * @param battery        % de bateria agora
     * @param trendPerDay    variação da bateria em pontos percentuais por dia
     * @param consumptionW   consumo médio em W
     * @param harvestedW     coleta média em W (média do dia inteiro)
     * @param temperature    temperatura média em °C
     * @param offlineMinutes há quantos minutos parou de transmitir (null = online)
     */
    public record Simulation(Double battery, Double trendPerDay, Double consumptionW,
                             Double harvestedW, Double temperature, Integer offlineMinutes) {
        static final Simulation DEFAULT = new Simulation(null, null, null, null, null, null);

        public Simulation {
            battery = battery != null ? battery : 70d;
            trendPerDay = trendPerDay != null ? trendPerDay : 0d;
            consumptionW = consumptionW != null ? consumptionW : 0.5;
            harvestedW = harvestedW != null ? harvestedW : 0d;
            temperature = temperature != null ? temperature : 25d;
        }
    }
}
