package br.com.ecosense.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;

import br.com.ecosense.api.ApiDtos.Alert;
import br.com.ecosense.api.ApiDtos.Battery;
import br.com.ecosense.api.ApiDtos.Consumption;
import br.com.ecosense.api.ApiDtos.Device;
import br.com.ecosense.api.ApiDtos.DeviceCount;
import br.com.ecosense.api.ApiDtos.ForecastItem;
import br.com.ecosense.api.ApiDtos.Generation;
import br.com.ecosense.api.ApiDtos.Load;
import br.com.ecosense.api.ApiDtos.Overview;
import br.com.ecosense.api.ApiDtos.Source;
import br.com.ecosense.api.ApiDtos.Storage;
import br.com.ecosense.config.EcoSenseProperties;
import br.com.ecosense.config.EcoSenseProperties.DeviceConfig;
import br.com.ecosense.config.EcoSenseProperties.StorageConfig;
import br.com.ecosense.service.ReadingService.DeviceData;

/** Visão geral, fontes de energia, cargas, baterias e alertas, derivados das leituras. */
@Service
public class EnergyService {

    private final ReadingService readings;
    private final DeviceService devices;
    private final HistoryService history;
    private final ForecastService forecasts;
    private final EcoSenseProperties props;
    private final Clock clock;

    public EnergyService(ReadingService readings, DeviceService devices, HistoryService history,
                         ForecastService forecasts, EcoSenseProperties props, Clock clock) {
        this.readings = readings;
        this.devices = devices;
        this.history = history;
        this.forecasts = forecasts;
        this.props = props;
        this.clock = clock;
    }

    /* ---------------- visão geral ---------------- */

    public Overview overview() {
        List<DeviceConfig> all = devices.configs();
        Period p = period();
        double genToday = history.energyKwh(all, p.todayStart, p.now, Reading::harvestedW);
        double genYesterday = history.energyKwh(all, p.yesterdayStart, p.sameTimeYesterday, Reading::harvestedW);
        double conToday = history.energyKwh(all, p.todayStart, p.now, Reading::consumptionW);
        double conYesterday = history.energyKwh(all, p.yesterdayStart, p.sameTimeYesterday, Reading::consumptionW);

        double nowW = 0;
        int online = 0;
        List<Double> deviceBatteries = new ArrayList<>();
        for (DeviceConfig d : all) {
            DeviceData data = readings.data(d);
            if (!devices.isOnline(d, data)) continue;
            online++;
            if (data.latest().consumptionW() != null) nowW += data.latest().consumptionW();
            deviceBatteries.add(data.latest().battery());
        }

        return new Overview(
                new Generation(Stats.sig(genToday), change(genToday, genYesterday)),
                new Consumption(Stats.sig(nowW), Stats.sig(conToday), change(conToday, conYesterday)),
                storage(deviceBatteries),
                new DeviceCount(all.size(), online, all.size() - online));
    }

    /** Com baterias do sistema configuradas: carga ponderada pela capacidade. Sem elas: média dos dispositivos. */
    private Storage storage(List<Double> deviceBatteries) {
        double stored = 0, capacity = 0;
        for (StorageConfig s : props.storage()) {
            Double pct = readings.storagePct(s);
            if (pct == null) continue;
            stored += pct / 100 * s.capacityKwh();
            capacity += s.capacityKwh();
        }
        if (capacity > 0) {
            int pct = (int) Math.round(stored / capacity * 100);
            return new Storage(pct, Stats.round(stored, 2), Stats.round(capacity, 2), statusFor(pct));
        }
        Double avg = Stats.mean(deviceBatteries);
        Integer pct = avg == null ? null : (int) Math.round(avg);
        return new Storage(pct, null, null, pct == null ? "Atenção" : statusFor(pct));
    }

    /* ---------------- fontes, cargas e baterias ---------------- */

    /** Uma fonte por tipo de coleta (Solar, Térmico…), somando os dispositivos daquele tipo. */
    public List<Source> sources() {
        Period p = period();
        Map<String, List<DeviceConfig>> byType = new LinkedHashMap<>();
        for (DeviceConfig d : devices.configs()) {
            if (d.harvestingType() != null) byType.computeIfAbsent(d.harvestingType(), k -> new ArrayList<>()).add(d);
        }
        List<Source> result = new ArrayList<>();
        byType.forEach((type, configs) -> {
            double kwh = history.energyKwh(configs, p.todayStart, p.now, Reading::harvestedW);
            boolean anyOnline = configs.stream().anyMatch(d -> devices.isOnline(d, readings.data(d)));
            Instant updatedAt = configs.stream().map(d -> readings.data(d).latest()).filter(Objects::nonNull)
                    .map(Reading::at).max(Comparator.naturalOrder())
                    .map(t -> t.truncatedTo(ChronoUnit.SECONDS)).orElse(null);
            /* eficiência exige saber a energia disponível no ambiente; null até existir esse dado */
            result.add(new Source(type, Stats.sig(kwh), null, anyOnline ? "Normal" : "Atenção", updatedAt));
        });
        return result;
    }

    /** Cada dispositivo como carga: consumo atual × média de 7 dias. */
    public List<Load> loads() {
        Instant weekAgo = clock.instant().minus(Duration.ofDays(7));
        return devices.configs().stream().map(d -> {
            DeviceData data = readings.data(d);
            boolean online = devices.isOnline(d, data);
            double current = online && data.latest().consumptionW() != null ? data.latest().consumptionW() : 0;
            Double avg = Stats.mean(DeviceService.since(data.hourly(), weekAgo), Reading::consumptionW);
            return new Load(d.name(), d.type(), Stats.sig(current), avg == null ? 0 : Stats.sig(avg),
                    d.priority(), loadStatus(online, data, current, avg));
        }).toList();
    }

    public List<Battery> batteries() {
        return props.storage().stream().map(s -> {
            Double pct = readings.storagePct(s);
            Integer rounded = pct == null ? null : (int) Math.round(pct);
            return new Battery(s.name(), s.type(), s.capacityKwh(), rounded,
                    rounded == null ? "Atenção" : statusFor(rounded));
        }).toList();
    }

    /* ---------------- alertas ---------------- */

    /** Alertas derivados do estado atual. Sem problemas, devolve um alerta "Sistema normal". */
    public List<Alert> alerts() {
        Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        List<Alert> alerts = new ArrayList<>();
        for (DeviceConfig d : devices.configs()) {
            DeviceData data = readings.data(d);
            Device dev = devices.toDevice(d);
            Instant at = dev.lastSeenAt() != null ? dev.lastSeenAt() : now;
            if (!"Online".equals(dev.status())) {
                alerts.add(new Alert(d.id() + ":offline", "high", "Dispositivo offline",
                        dev.name() + " (" + dev.location() + ") não está transmitindo dados.", at));
                continue;
            }
            ForecastItem f = forecasts.item(d);
            if (dev.battery() < 20) {
                alerts.add(new Alert(d.id() + ":battery", "critical", "Bateria crítica",
                        dev.name() + " está com " + dev.battery() + "% de bateria.", at));
            } else if (f.daysLeft() != null && f.daysLeft() <= 3) {
                alerts.add(new Alert(d.id() + ":depleting", "critical", "Bateria esgotando",
                        dev.name() + " deve ficar sem bateria em " + f.daysLeft()
                                + (f.daysLeft() == 1 ? " dia." : " dias."), at));
            }
            Double avg = Stats.mean(DeviceService.since(data.hourly(), now.minus(Duration.ofDays(7))), Reading::consumptionW);
            Double current = data.latest().consumptionW();
            if (current != null && avg != null && avg > 0 && current > 1.5 * avg) {
                alerts.add(new Alert(d.id() + ":consumption", "high", "Consumo elevado",
                        dev.name() + " está consumindo " + fmt(current) + ", acima da média de " + fmt(avg) + ".", at));
            }
        }
        Period p = period();
        double genToday = history.energyKwh(devices.configs(), p.todayStart, p.now, Reading::harvestedW);
        double genYesterday = history.energyKwh(devices.configs(), p.yesterdayStart, p.sameTimeYesterday, Reading::harvestedW);
        if (genYesterday > 0 && genToday < 0.7 * genYesterday) {
            alerts.add(new Alert("generation:low", "warning", "Baixa geração",
                    "A geração de hoje está " + Math.round((1 - genToday / genYesterday) * 100)
                            + "% abaixo de ontem no mesmo horário.", now));
        }
        if (alerts.isEmpty()) {
            alerts.add(new Alert("system:ok", "info", "Sistema normal",
                    "Todos os dispositivos estão funcionando normalmente.", now));
        }
        alerts.sort(Comparator.comparingInt((Alert a) -> severity(a.level()))
                .thenComparing(Alert::createdAt, Comparator.reverseOrder()));
        return alerts;
    }

    /* ---------------- auxiliares ---------------- */

    private record Period(Instant now, Instant todayStart, Instant yesterdayStart, Instant sameTimeYesterday) {}

    private Period period() {
        Instant now = clock.instant();
        Instant todayStart = LocalDate.ofInstant(now, props.zone()).atStartOfDay(props.zone()).toInstant();
        return new Period(now, todayStart, todayStart.minus(Duration.ofDays(1)), now.minus(Duration.ofDays(1)));
    }

    private static Double change(double today, double yesterday) {
        return yesterday > 0 ? Stats.round((today - yesterday) / yesterday * 100, 1) : null;
    }

    private static String statusFor(int pct) {
        return pct < 20 ? "Crítico" : pct < 45 ? "Atenção" : "Normal";
    }

    private static String loadStatus(boolean online, DeviceData data, double current, Double avg) {
        if (!online) return "Crítico";
        Double battery = data.latest().battery();
        if (battery != null && battery < 20) return "Crítico";
        if (avg != null && avg > 0 && current > 1.5 * avg) return "Atenção";
        return "Normal";
    }

    private static int severity(String level) {
        return switch (level) {
            case "critical" -> 0;
            case "high" -> 1;
            case "warning" -> 2;
            default -> 3;
        };
    }

    /** Potência com a unidade adequada: "1,2 W", "0,45 mW", "80 µW". */
    static String fmt(double w) {
        java.util.Locale br = java.util.Locale.of("pt", "BR");
        double abs = Math.abs(w);
        if (abs >= 1 || abs == 0) return String.format(br, "%.1f W", w);
        if (abs >= 0.0001) return String.format(br, "%.2f mW", w * 1000);
        return String.format(br, "%.0f µW", w * 1_000_000);
    }
}
