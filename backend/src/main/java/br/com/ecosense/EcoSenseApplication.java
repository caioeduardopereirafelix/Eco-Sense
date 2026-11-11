package br.com.ecosense;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class EcoSenseApplication {

    public static void main(String[] args) {
        SpringApplication.run(EcoSenseApplication.class, args);
    }
}
