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
     * @param sim            parâmetros do modo simulado (ignorado com o ThingSpeak real)
     */
    public record DeviceConfig(
            String id, String name, String location, String region,
            Double latitude, Double longitude,
            Integer txInterval, String type, String priority,
            String harvestingType, Double sunHours,
            Long channelId, String readApiKey,
            Fields fields, Simulation sim) {

        public DeviceConfig {
            txInterval = txInterval != null ? txInterval : 5;
            type = type != null ? type : "Sensor";
            priority = priority != null ? priority : "Média";
            fields = fields != null ? fields : new Fields(null, null, null, null);
            sim = sim != null ? sim : Simulation.DEFAULT;
        }
    }

    /**
     * Em qual field do canal está cada leitura. Unidades esperadas no ThingSpeak:
     * battery em %, consumption em W, temperature em °C, harvested em W.
     */
    public record Fields(String battery, String consumption, String temperature, String harvested) {
        public Fields {
            battery = battery != null ? battery : "field1";
            consumption = consumption != null ? consumption : "field2";
            temperature = temperature != null ? temperature : "field3";
            harvested = harvested != null ? harvested : "field4";
        }
    }

    /** Bateria/armazenamento do sistema: lê o percentual de carga de um field de um canal. */
    public record StorageConfig(
            String name, String type, Double capacityKwh,
            Long channelId, String readApiKey, String field, Simulation sim) {

        public StorageConfig {
            type = type != null ? type : "Bateria";
            capacityKwh = capacityKwh != null ? capacityKwh : 0d;
            field = field != null ? field : "field1";
            sim = sim != null ? sim : Simulation.DEFAULT;
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
