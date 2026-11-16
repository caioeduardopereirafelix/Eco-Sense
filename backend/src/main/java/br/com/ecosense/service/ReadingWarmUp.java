package br.com.ecosense.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "ecosense", name = "warm-up", havingValue = "true", matchIfMissing = true)
public class ReadingWarmUp {

    private static final Logger log = LoggerFactory.getLogger(ReadingWarmUp.class);

    private final ReadingService readings;
    private final DeviceCatalog catalog;

    public ReadingWarmUp(ReadingService readings, DeviceCatalog catalog) {
        this.readings = readings;
        this.catalog = catalog;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        Thread.ofVirtual().name("ecosense-warm-up").start(this::run);
    }

    void run() {
        long start = System.nanoTime();
        try {
            var devices = catalog.all();
            log.info("Carregando as leituras de {} dispositivos do ThingSpeak em segundo plano…", devices.size());
            readings.warmUp(devices);
            log.info("Leituras carregadas em {} s: o painel já abre com os dados.",
                    String.format("%.1f", (System.nanoTime() - start) / 1e9));
        } catch (RuntimeException e) {
            log.warn("Não foi possível carregar as leituras ao ligar: {}", e.getMessage());
        }
    }
}
