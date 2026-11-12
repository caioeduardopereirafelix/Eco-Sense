package br.com.ecosense.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.ecosense.api.ApiDtos.MaintenanceRequest;
import br.com.ecosense.api.ApiDtos.MaintenanceUpdate;
import br.com.ecosense.api.ApiDtos.NewMaintenanceRequest;
import br.com.ecosense.api.ApiException;
import br.com.ecosense.config.EcoSenseProperties;
import br.com.ecosense.persistence.MaintenanceRequestEntity;
import br.com.ecosense.persistence.MaintenanceRequestRepository;
import br.com.ecosense.security.AuthUser;

/** Solicitações de manutenção, guardadas no banco (tabela maintenance_requests). */
@Service
public class MaintenanceService {

    public static final List<String> STATUSES = List.of("Recebida", "Agendada", "Em rota", "Concluída");

    private final MaintenanceRequestRepository repository;
    private final DeviceService devices;
    private final EcoSenseProperties props;
    private final Clock clock;

    public MaintenanceService(MaintenanceRequestRepository repository, DeviceService devices,
                              EcoSenseProperties props, Clock clock) {
        this.repository = repository;
        this.devices = devices;
        this.props = props;
        this.clock = clock;
    }

    /** Administrador vê todos os pedidos; usuário comum, só os que ele mesmo fez. */
    public List<MaintenanceRequest> list(AuthUser user) {
        var all = user.isAdmin() ? repository.findAllByOrderByIdAsc()
                : repository.findAllByRequestedByIdOrderByIdAsc(user.id());
        return all.stream().map(MaintenanceService::toDto).toList();
    }

    @Transactional
    public MaintenanceRequest create(NewMaintenanceRequest body, AuthUser user) {
        devices.config(body.deviceId()); // 404 se o dispositivo não existir
        MaintenanceRequestEntity e = new MaintenanceRequestEntity();
        e.setDeviceId(body.deviceId());
        e.setReason(body.reason().trim());
        e.setNote(body.note() == null ? "" : body.note().trim());
        e.setStatus("Recebida");
        e.setCreatedAt(Instant.now(clock).truncatedTo(ChronoUnit.SECONDS));
        e.setRequestedById(user.id());
        e.setRequestedByName(user.name());
        return toDto(repository.save(e));
    }

    /**
     * Regras: data vazia mantém a já agendada; voltar para "Recebida" limpa a data;
     * "Agendada" exige data válida e não passada.
     */
    @Transactional
    public MaintenanceRequest update(long id, MaintenanceUpdate body) {
        if (!STATUSES.contains(body.status())) throw ApiException.badRequest("Status de manutenção inválido.");
        MaintenanceRequestEntity e = repository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Solicitação de manutenção não encontrada."));

        LocalDate day = parseDay(body.scheduledFor());
        if ("Recebida".equals(body.status())) day = null;
        else if (day == null) day = e.getScheduledFor();
        if ("Agendada".equals(body.status())) {
            if (day == null) throw ApiException.badRequest("Informe uma data válida para o agendamento.");
            if (day.isBefore(LocalDate.now(clock.withZone(props.zone()))))
                throw ApiException.badRequest("A data do agendamento não pode estar no passado.");
        }
        e.setStatus(body.status());
        e.setScheduledFor(day);
        return toDto(repository.save(e));
    }

    private static MaintenanceRequest toDto(MaintenanceRequestEntity e) {
        return new MaintenanceRequest(e.getId(), e.getDeviceId(), e.getReason(), e.getNote(),
                e.getStatus(), e.getCreatedAt(), e.getScheduledFor(), e.getRequestedByName());
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
