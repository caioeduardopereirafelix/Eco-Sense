package br.com.ecosense.api;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.ecosense.api.ApiDtos.Alert;
import br.com.ecosense.api.ApiDtos.Battery;
import br.com.ecosense.api.ApiDtos.Device;
import br.com.ecosense.api.ApiDtos.ForecastSeries;
import br.com.ecosense.api.ApiDtos.Forecasts;
import br.com.ecosense.api.ApiDtos.History;
import br.com.ecosense.api.ApiDtos.Load;
import br.com.ecosense.api.ApiDtos.MaintenanceRequest;
import br.com.ecosense.api.ApiDtos.MaintenanceUpdate;
import br.com.ecosense.api.ApiDtos.NewMaintenanceRequest;
import br.com.ecosense.api.ApiDtos.Overview;
import br.com.ecosense.api.ApiDtos.Source;
import br.com.ecosense.security.AuthUser;
import br.com.ecosense.service.DeviceGroup;
import br.com.ecosense.service.DeviceService;
import br.com.ecosense.service.EnergyService;
import br.com.ecosense.service.ForecastService;
import br.com.ecosense.service.HistoryService;
import br.com.ecosense.service.MaintenanceService;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final EnergyService energy;
    private final DeviceService devices;
    private final ForecastService forecasts;
    private final HistoryService history;
    private final MaintenanceService maintenance;

    public ApiController(EnergyService energy, DeviceService devices, ForecastService forecasts,
                         HistoryService history, MaintenanceService maintenance) {
        this.energy = energy;
        this.devices = devices;
        this.forecasts = forecasts;
        this.history = history;
        this.maintenance = maintenance;
    }

    @GetMapping("/overview")
    public Overview overview(@RequestParam(required = false) String group) {
        return energy.overview(DeviceGroup.parse(group));
    }

    @GetMapping("/sources")
    public List<Source> sources(@RequestParam(required = false) String group) {
        return energy.sources(DeviceGroup.parse(group));
    }

    @GetMapping("/loads")
    public List<Load> loads() {
        return energy.loads();
    }

    @GetMapping("/batteries")
    public List<Battery> batteries() {
        return energy.batteries();
    }

    @GetMapping("/devices")
    public List<Device> devices() {
        return devices.devices();
    }

    @GetMapping("/devices/{id}")
    public Device device(@PathVariable String id) {
        return devices.device(id);
    }

    @GetMapping("/alerts")
    public List<Alert> alerts() {
        return energy.alerts();
    }

    @GetMapping("/forecasts")
    public Forecasts forecasts() {
        return forecasts.forecasts();
    }

    @GetMapping("/devices/{id}/forecast")
    public ForecastSeries forecastSeries(@PathVariable String id, @RequestParam(defaultValue = "24h") String range) {
        return forecasts.series(id, range);
    }

    @GetMapping("/history")
    public History history(
            @RequestParam(defaultValue = "hoje") String range,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String group) {
        return history.history(range, from, to, DeviceGroup.parse(group));
    }

    @GetMapping("/maintenance-requests")
    public List<MaintenanceRequest> maintenanceRequests(@AuthenticationPrincipal AuthUser user) {
        return maintenance.list(user);
    }

    @PostMapping("/maintenance-requests")
    @ResponseStatus(HttpStatus.CREATED)
    public MaintenanceRequest requestMaintenance(@AuthenticationPrincipal AuthUser user,
                                                 @Valid @RequestBody NewMaintenanceRequest body) {
        return maintenance.create(body, user);
    }

    @PatchMapping("/maintenance-requests/{id}")
    public MaintenanceRequest updateMaintenance(@PathVariable long id, @Valid @RequestBody MaintenanceUpdate body) {
        return maintenance.update(id, body);
    }
}
