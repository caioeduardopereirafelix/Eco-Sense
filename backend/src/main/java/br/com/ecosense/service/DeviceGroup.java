package br.com.ecosense.service;

import java.util.Locale;
import java.util.Set;

import br.com.ecosense.api.ApiException;
import br.com.ecosense.config.EcoSenseProperties.DeviceConfig;

/**
 * Categoria de dispositivo para os totais: sensores IoT (energia em mW/mWh) e sistemas de energia
 * (instalações solares e residências, em W/kWh) têm escalas muito diferentes; somados, os sistemas
 * escondem os sensores. A categoria vem do campo "tipo" do cadastro.
 */
public enum DeviceGroup {
    ALL("todos"),
    /** sensores, comunicação, atuadores, vídeo… (tudo que não é sistema de energia) */
    IOT("iot"),
    /** tipos "Sistema solar" e "Residência" */
    SYSTEMS("sistemas");

    private static final Set<String> SYSTEM_TYPES = Set.of("sistema solar", "residência");

    private final String param;

    DeviceGroup(String param) {
        this.param = param;
    }

    /** Valor do parâmetro {@code group} da API: todos (ou vazio) | iot | sistemas. */
    public static DeviceGroup parse(String value) {
        if (value == null || value.isBlank()) return ALL;
        for (DeviceGroup g : values()) {
            if (g.param.equalsIgnoreCase(value.trim())) return g;
        }
        throw ApiException.badRequest("Grupo inválido. Use todos, iot ou sistemas.");
    }

    /** Categoria de um dispositivo pelo tipo cadastrado (nunca ALL). */
    public static DeviceGroup of(DeviceConfig d) {
        String type = d.type() == null ? "" : d.type().trim().toLowerCase(Locale.ROOT);
        return SYSTEM_TYPES.contains(type) ? SYSTEMS : IOT;
    }

    public boolean includes(DeviceConfig d) {
        return this == ALL || of(d) == this;
    }

    public String param() {
        return param;
    }
}
