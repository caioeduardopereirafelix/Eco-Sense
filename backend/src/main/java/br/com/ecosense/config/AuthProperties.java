package br.com.ecosense.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ecosense.auth")
public record AuthProperties(String jwtSecret, Duration tokenTtl,
                             String adminEmail, String adminName, String adminPassword) {

    public AuthProperties {
        tokenTtl = tokenTtl != null ? tokenTtl : Duration.ofHours(8);
        adminEmail = adminEmail != null && !adminEmail.isBlank() ? adminEmail.trim() : "admin@ecosense.local";
        adminName = adminName != null && !adminName.isBlank() ? adminName.trim() : "Administrador";
    }
}
