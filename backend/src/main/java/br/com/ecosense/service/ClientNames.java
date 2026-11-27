package br.com.ecosense.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import br.com.ecosense.persistence.DeviceRepository;

@Component
@Order(1)
public class ClientNames implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ClientNames.class);

    record Rename(String id, String oldName, String name, String location) {}

    static final List<Rename> RENAMES = List.of(
            new Rename("IOT-001", "Harveo – coletor solar", "Agro Báltico · Sensor de estufa", "Agro Báltico – Estufa 2"),
            new Rename("IOT-002", "EMBEDO Node – sensores", "Agro Báltico · Central de sensores", "Agro Báltico – Galpão de controle"),
            new Rename("IOT-003", "Usina solar 12 V", "EnerSol Polska · Estação solar 12 V", "EnerSol Polska – Usina rural"),
            new Rename("IOT-004", "Estação solar Yokohama", "Kanagawa Logística · Estação de clima", "Kanagawa Logística – Pátio de contêineres"),
            new Rename("IOT-005", "Casa solar 14,5 kWp", "Condomínio Alpenblick · Solar 14,5 kWp", "Condomínio Alpenblick – Cobertura"),
            new Rename("IOT-006", "Banco solar Texas", "Rancho Lone Star · Banco solar", "Rancho Lone Star – Bombeamento de água"),
            new Rename("IOT-007", "Estação de energia Nebraska", "Cooperativa Pradaria · Estação de energia", "Cooperativa Pradaria – Silo central"),
            new Rename("IOT-008", "DFRobot SPV1050 – coletor solar", "Agro Báltico · Sensor de umidade", "Agro Báltico – Estufa 5"),
            new Rename("IOT-009", "Estação meteorológica Volta", "Vinícola Bella Vista · Estação de clima", "Vinícola Bella Vista – Vinhedo norte"),
            new Rename("IOT-010", "Repetidor Wi-Fi solar", "Rede Rural Kiwi · Repetidor Wi-Fi", "Rede Rural Kiwi – Torre 3"));

    private final DeviceRepository repository;
    private final DeviceCatalog catalog;

    public ClientNames(DeviceRepository repository, DeviceCatalog catalog) {
        this.repository = repository;
        this.catalog = catalog;
    }

    @Override
    public void run(ApplicationArguments args) {
        int changed = 0;
        for (Rename r : RENAMES) {
            var found = repository.findById(r.id());
            if (found.isEmpty() || !r.oldName().equals(found.get().getName())) continue;
            var e = found.get();
            e.setName(r.name());
            e.setLocation(r.location());
            repository.save(e);
            changed++;
        }
        if (changed > 0) {
            catalog.refresh();
            log.info("{} dispositivo(s) renomeado(s) para os clientes de exemplo.", changed);
        }
    }
}
