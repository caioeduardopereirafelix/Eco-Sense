package br.com.ecosense.thingspeak;

import java.time.Instant;
import java.util.Map;

public record Feed(Instant createdAt, Map<String, String> fields) {

    public Double number(String field) {
        if (field == null) return null;
        String raw = fields.get(field);
        if (raw == null || raw.isBlank()) return null;
        try {
            double value = Double.parseDouble(raw.trim());
            return Double.isFinite(value) ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
