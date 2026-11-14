package br.com.ecosense.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class ApiDtos {

    private ApiDtos() {}

    public record Overview(Generation generation, Consumption consumption, Storage storage, DeviceCount devices) {}
    public record Generation(double todayKwh, Double changePct) {}
    public record Consumption(double nowW, double todayKwh, Double changePct) {}
    public record Storage(Integer pct, Double storedKwh, Double capacityKwh, String status) {}
    public record DeviceCount(int total, int online, int offline) {}

    public record Source(String name, double energyKwh, Double efficiencyPct, String status, Instant updatedAt,
                         List<SourceDevice> devices) {}
    public record SourceDevice(String id, String name, String group, double energyKwh, Double sharePct) {}
    public record Load(String name, String type, Double currentW, Double avgW, String priority, String status) {}
    public record Battery(String name, String type, double capacityKwh, Integer pct, String status) {}

    public record Device(String id, String name, String location, String region,
                         Double latitude, Double longitude, String status,
                         int battery, Double consumption, Instant lastSeenAt,
                         Double temperature, int txInterval, Harvesting harvesting) {}
    public record Harvesting(String type, Double collected) {}

    public record Alert(String id, String level, String title, String message, Instant createdAt) {}

    public record Forecasts(String season, Instant updatedAt, List<ForecastItem> items) {}
    public record ForecastItem(String id, Integer daysLeft, boolean sustainable, boolean deficit,
                               Double balanceWhDay, Integer confidence, Double sunHours) {}

    public record ForecastSeries(String step, List<Instant> timestamps, int nowIndex, Series consumption, Series battery) {}
    public record Series(List<Double> measured, List<Double> predicted) {}

    public record History(String step, List<Instant> timestamps,
                          List<Double> generatedKwh, List<Double> consumedKwh, List<Double> storagePct) {}

    public record MaintenanceRequest(long id, String deviceId, String reason, String note,
                                     String status, Instant createdAt, LocalDate scheduledFor,
                                     String requestedBy) {}

    public record NewMaintenanceRequest(
            @NotBlank(message = "Informe o dispositivo.") String deviceId,
            @NotBlank(message = "Informe o motivo.") @Size(max = 100) String reason,
            @Size(max = 200, message = "A observação deve ter até 200 caracteres.") String note) {}

    public record MaintenanceUpdate(
            @NotBlank(message = "Informe o status.") String status,
            String scheduledFor) {}

    public record ErrorBody(String message) {}

    public record LoginRequest(
            @NotBlank(message = "Informe o e-mail.") String email,
            @NotBlank(message = "Informe a senha.") String password) {}

    public record UserInfo(long id, String name, String email, String role, Instant createdAt) {}

    public record Session(String token, Instant expiresAt, UserInfo user) {}

    public record ProfileUpdate(
            @NotBlank(message = "Informe o nome.") @Size(max = 80, message = "O nome deve ter até 80 caracteres.") String name) {}

    public record PasswordChange(
            @NotBlank(message = "Informe a senha atual.") String currentPassword,
            @NotBlank(message = "Informe a nova senha.")
            @Size(min = 8, max = 72, message = "A nova senha deve ter de 8 a 72 caracteres.") String newPassword) {}

    public record NewUser(
            @NotBlank(message = "Informe o nome.") @Size(max = 80, message = "O nome deve ter até 80 caracteres.") String name,
            @NotBlank(message = "Informe o e-mail.") @Email(message = "Informe um e-mail válido.")
            @Size(max = 120, message = "O e-mail deve ter até 120 caracteres.") String email,
            @NotBlank(message = "Informe o perfil.") @Pattern(regexp = "ADMIN|USER", message = "Perfil inválido (use ADMIN ou USER).") String role,
            @NotBlank(message = "Informe a senha inicial.")
            @Size(min = 8, max = 72, message = "A senha deve ter de 8 a 72 caracteres.") String password) {}

    public record UserUpdate(
            @NotBlank(message = "Informe o nome.") @Size(max = 80, message = "O nome deve ter até 80 caracteres.") String name,
            @NotBlank(message = "Informe o e-mail.") @Email(message = "Informe um e-mail válido.")
            @Size(max = 120, message = "O e-mail deve ter até 120 caracteres.") String email,
            @NotBlank(message = "Informe o perfil.") @Pattern(regexp = "ADMIN|USER", message = "Perfil inválido (use ADMIN ou USER).") String role) {}

    public record PasswordReset(
            @NotBlank(message = "Informe a nova senha.")
            @Size(min = 8, max = 72, message = "A senha deve ter de 8 a 72 caracteres.") String password) {}

    public record DeviceSettings(
            @Pattern(regexp = "[A-Za-z0-9_-]{1,40}", message = "O ID deve ter até 40 caracteres: letras, números, - ou _.")
            String id,
            @NotBlank(message = "Informe o nome.") @Size(max = 80, message = "O nome deve ter até 80 caracteres.") String name,
            @Size(max = 120) String location,
            @Size(max = 60) String region,
            @DecimalMin(value = "-90", message = "Latitude inválida.") @DecimalMax(value = "90", message = "Latitude inválida.") Double latitude,
            @DecimalMin(value = "-180", message = "Longitude inválida.") @DecimalMax(value = "180", message = "Longitude inválida.") Double longitude,
            @Min(value = 1, message = "O intervalo de transmissão deve ser de 1 a 1440 minutos.") @Max(1440) Integer txInterval,
            @Size(max = 40) String type,
            @Pattern(regexp = "Alta|Média|Baixa", message = "Prioridade inválida.") String priority,
            @Size(max = 40) String harvestingType,
            @DecimalMin(value = "0", message = "Horas de sol inválidas.") @DecimalMax(value = "24", message = "Horas de sol inválidas.") Double sunHours,
            @Min(value = 1, message = "Channel ID inválido.") Long channelId,
            @Min(value = 1, message = "Use de 1 a 31 dias por pedido.") @Max(31) Integer historyChunkDays,
            @Valid FieldMapping fields,
            ConversionSettings conversion) {}

    public record FieldMapping(
            @Pattern(regexp = "field[1-8]|none", message = "Field inválido (use field1 a field8 ou none).") String battery,
            @Pattern(regexp = "field[1-8]|none", message = "Field inválido (use field1 a field8 ou none).") String consumption,
            @Pattern(regexp = "field[1-8]|none", message = "Field inválido (use field1 a field8 ou none).") String temperature,
            @Pattern(regexp = "field[1-8]|none", message = "Field inválido (use field1 a field8 ou none).") String harvested) {}

    public record ConversionSettings(Double batteryVoltageMin, Double batteryVoltageMax,
                                     Double consumptionScale, Double harvestedScale, Double temperatureScale) {}

    public record ChannelInfo(long channelId, String name, String description, Double latitude, Double longitude,
                              java.util.Map<String, String> fields, Instant lastEntryAt) {}
}
