package br.com.ecosense.service;

import java.util.Locale;
import java.util.Set;

import br.com.ecosense.api.ApiException;
import br.com.ecosense.config.EcoSenseProperties.DeviceConfig;

public enum DeviceGroup {
    ALL("todos"),
    IOT("iot"),
    SYSTEMS("sistemas");

    private static final Set<String> SYSTEM_TYPES = Set.of("sistema solar", "residência");

    private final String param;

    DeviceGroup(String param) {
        this.param = param;
    }

    public static DeviceGroup parse(String value) {
        if (value == null || value.isBlank()) return ALL;
        for (DeviceGroup g : values()) {
            if (g.param.equalsIgnoreCase(value.trim())) return g;
        }
        throw ApiException.badRequest("Grupo inválido. Use todos, iot ou sistemas.");
    }

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
