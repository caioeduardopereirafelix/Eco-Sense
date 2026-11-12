package br.com.ecosense.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Login (prefixo {@code ecosense.auth}). Os segredos vêm do .env, nunca do YAML versionado.
 *
 * @param jwtSecret     chave que assina os tokens (JWT_SECRET, mínimo 32 caracteres). Vazia = chave
 *                      aleatória a cada início do back-end (todos precisam entrar de novo após reiniciar).
 * @param tokenTtl      validade do token de acesso (padrão 8 h)
 * @param adminEmail    e-mail do primeiro administrador, criado quando não há nenhum usuário
 * @param adminName     nome do primeiro administrador
 * @param adminPassword senha do primeiro administrador (ECOSENSE_ADMIN_PASSWORD). Vazia = senha
 *                      aleatória, mostrada uma única vez no log de inicialização.
 */
@ConfigurationProperties(prefix = "ecosense.auth")
public record AuthProperties(String jwtSecret, Duration tokenTtl,
                             String adminEmail, String adminName, String adminPassword) {

    public AuthProperties {
        tokenTtl = tokenTtl != null ? tokenTtl : Duration.ofHours(8);
        adminEmail = adminEmail != null && !adminEmail.isBlank() ? adminEmail.trim() : "admin@ecosense.local";
        adminName = adminName != null && !adminName.isBlank() ? adminName.trim() : "Administrador";
    }
}
