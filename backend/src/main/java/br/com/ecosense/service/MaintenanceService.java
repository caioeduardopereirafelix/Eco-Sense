package br.com.ecosense.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Service;

import br.com.ecosense.api.ApiDtos.MaintenanceRequest;
import br.com.ecosense.api.ApiDtos.MaintenanceUpdate;
import br.com.ecosense.api.ApiDtos.NewMaintenanceRequest;
import br.com.ecosense.api.ApiException;
import br.com.ecosense.config.EcoSenseProperties;

/**
 * Solicitações de manutenção. Guardadas em memória (somem ao reiniciar):
 * o próximo passo é trocar por um repositório com banco de dados (ex.: Spring Data JPA).
 */
@Service
public class MaintenanceService {

    public static final List<String> STATUSES = List.of("Recebida", "Agendada", "Em rota", "Concluída");

    private final DeviceService devices;
    private final EcoSenseProperties props;
    private final Clock clock;
    private final Map<Long, MaintenanceRequest> store = new ConcurrentHashMap<>();
    private final AtomicLong ids = new AtomicLong();

    public MaintenanceService(DeviceService devices, EcoSenseProperties props, Clock clock) {
        this.devices = devices;
        this.props = props;
        this.clock = clock;
    }

    public List<MaintenanceRequest> list() {
        List<MaintenanceRequest> all = new ArrayList<>(store.values());
        all.sort((a, b) -> Long.compare(a.id(), b.id()));
        return all;
    }

    public MaintenanceRequest create(NewMaintenanceRequest body) {
        devices.config(body.deviceId()); // 404 se o dispositivo não existir
        long id = ids.incrementAndGet();
        MaintenanceRequest r = new MaintenanceRequest(id, body.deviceId(), body.reason().trim(),
                body.note() == null ? "" : body.note().trim(), "Recebida",
                Instant.now(clock).truncatedTo(ChronoUnit.SECONDS), null);
        store.put(id, r);
        return r;
    }

    /**
     * Regras: data vazia mantém a já agendada; voltar para "Recebida" limpa a data;
     * "Agendada" exige data válida e não passada.
     */
    public synchronized MaintenanceRequest update(long id, MaintenanceUpdate body) {
        if (!STATUSES.contains(body.status())) throw ApiException.badRequest("Status de manutenção inválido.");
        MaintenanceRequest current = store.get(id);
        if (current == null) throw ApiException.notFound("Solicitação de manutenção não encontrada.");

        LocalDate day = parseDay(body.scheduledFor());
        if ("Recebida".equals(body.status())) day = null;
        else if (day == null) day = current.scheduledFor();
        if ("Agendada".equals(body.status())) {
            if (day == null) throw ApiException.badRequest("Informe uma data válida para o agendamento.");
            if (day.isBefore(LocalDate.now(clock.withZone(props.zone()))))
                throw ApiException.badRequest("A data do agendamento não pode estar no passado.");
        }
        MaintenanceRequest updated = new MaintenanceRequest(current.id(), current.deviceId(), current.reason(),
                current.note(), body.status(), current.createdAt(), day);
        store.put(id, updated);
        return updated;
    }

    private static LocalDate parseDay(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw ApiException.badRequest("Informe uma data válida para o agendamento.");
        }
    }
}
