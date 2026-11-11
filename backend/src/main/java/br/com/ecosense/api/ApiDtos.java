package br.com.ecosense.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Formatos de resposta da API, exatamente como descritos em docs/API.md.
 * Números puros (unidade no nome do campo) e datas em ISO 8601.
 */
public final class ApiDtos {

    private ApiDtos() {}

    public record Overview(Generation generation, Consumption consumption, Storage storage, DeviceCount devices) {}
    public record Generation(double todayKwh, Double changePct) {}
    public record Consumption(double nowW, double todayKwh, Double changePct) {}
    /** storedKwh/capacityKwh são null quando não há armazenamento configurado (pct = média dos dispositivos). */
    public record Storage(Integer pct, Double storedKwh, Double capacityKwh, String status) {}
    public record DeviceCount(int total, int online, int offline) {}

    public record Source(String name, double energyKwh, Double efficiencyPct, String status, Instant updatedAt) {}
    public record Load(String name, String type, double currentW, double avgW, String priority, String status) {}
    public record Battery(String name, String type, double capacityKwh, Integer pct, String status) {}

    /** battery em %, consumption e harvesting.collected em Wh/dia, temperature em °C, txInterval em min. */
    public record Device(String id, String name, String location, String region,
                         Double latitude, Double longitude, String status,
                         int battery, double consumption, Instant lastSeenAt,
                         Double temperature, int txInterval, Harvesting harvesting) {}
    public record Harvesting(String type, double collected) {}

    /** level: critical | high | warning | info */
    public record Alert(String id, String level, String title, String message, Instant createdAt) {}

    public record Forecasts(String season, Instant updatedAt, List<ForecastItem> items) {}
    public record ForecastItem(String id, Integer daysLeft, boolean sustainable, Integer confidence, Double sunHours) {}

    public record ForecastSeries(String step, List<Instant> timestamps, int nowIndex, Series consumption, Series battery) {}
    public record Series(List<Double> measured, List<Double> predicted) {}

    public record History(String step, List<Instant> timestamps,
                          List<Double> generatedKwh, List<Double> consumedKwh, List<Double> storagePct) {}

    public record MaintenanceRequest(long id, String deviceId, String reason, String note,
                                     String status, Instant createdAt, LocalDate scheduledFor) {}

    public record NewMaintenanceRequest(
            @NotBlank(message = "Informe o dispositivo.") String deviceId,
            @NotBlank(message = "Informe o motivo.") @Size(max = 100) String reason,
            @Size(max = 200, message = "A observação deve ter até 200 caracteres.") String note) {}

    public record MaintenanceUpdate(
            @NotBlank(message = "Informe o status.") String status,
            /* AAAA-MM-DD; vazio/null mantém a data atual */
            String scheduledFor) {}

    public record ErrorBody(String message) {}
}
