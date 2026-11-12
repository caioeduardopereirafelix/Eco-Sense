package br.com.ecosense.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class AppConfig implements WebMvcConfigurer {

    private final EcoSenseProperties props;

    public AppConfig(EcoSenseProperties props) {
        this.props = props;
    }

    /** Relógio injetável: permite testar regras que dependem de "agora". */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /** Libera o front-end (servido em outra porta/origem) para chamar a API. */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        if (props.corsOrigins().isEmpty()) return;
        registry.addMapping("/api/**")
                .allowedOrigins(props.corsOrigins().toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
    }
}
