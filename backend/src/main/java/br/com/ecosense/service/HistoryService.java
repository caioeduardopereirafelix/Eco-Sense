package br.com.ecosense.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.springframework.stereotype.Service;

import br.com.ecosense.api.ApiDtos.History;
import br.com.ecosense.api.ApiException;
import br.com.ecosense.config.EcoSenseProperties;
import br.com.ecosense.config.EcoSenseProperties.DeviceConfig;

/**
 * Histórico agregado dos dispositivos (todos ou de uma categoria).
 * Cada leitura horária do ThingSpeak é a média de potência (W) naquela hora, então a energia da
 * hora é W × 1 h; somando as horas de um período obtém-se a energia do período (kWh = Wh / 1000).
 */
@Service
public class HistoryService {

    private final ReadingService readings;
    private final DeviceService devices;
    private final EcoSenseProperties props;
    private final Clock clock;

    public HistoryService(ReadingService readings, DeviceService devices, EcoSenseProperties props, Clock clock) {
        this.readings = readings;
        this.devices = devices;
        this.props = props;
        this.clock = clock;
    }

    /**
     * range: hoje | 7d | 30d | custom (custom exige from e to, até {@value ReadingService#HISTORY_DAYS} dias).
     * group: categoria de dispositivos somada (ALL = todos).
     */
    public History history(String range, LocalDate from, LocalDate to, DeviceGroup group) {
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, props.zone());
        boolean hourly = "hoje".equals(range);
        ZonedDateTime start;
        int points;
        switch (range == null ? "" : range) {
            case "hoje" -> { start = today.atStartOfDay(props.zone()); points = 24; }
            case "7d" -> { start = today.minusDays(6).atStartOfDay(props.zone()); points = 7; }
            case "30d" -> { start = today.minusDays(29).atStartOfDay(props.zone()); points = 30; }
            case "custom" -> {
                if (from == null || to == null) throw ApiException.badRequest("Informe as datas inicial e final.");
                if (from.isAfter(to)) throw ApiException.badRequest("A data inicial deve ser anterior à final.");
                if (to.isAfter(today)) throw ApiException.badRequest("A data final não pode estar no futuro.");
                if (from.isBefore(today.minusDays(ReadingService.HISTORY_DAYS - 1)))
                    throw ApiException.badRequest("O histórico cobre só os últimos " + ReadingService.HISTORY_DAYS + " dias.");
                start = from.atStartOfDay(props.zone());
                points = (int) ChronoUnit.DAYS.between(from, to) + 1;
            }
            default -> throw ApiException.badRequest("Período de histórico inválido. Use hoje, 7d, 30d ou custom.");
        }

        List<List<Reading>> perDevice = devices.configs(group).stream().map(d -> readings.data(d).hourly()).toList();
        List<Instant> timestamps = new ArrayList<>();
        List<Double> generated = new ArrayList<>(), consumed = new ArrayList<>(), storage = new ArrayList<>();
        for (int i = 0; i < points; i++) {
            ZonedDateTime slotStart = hourly ? start.plusHours(i) : start.plusDays(i);
            ZonedDateTime slotEnd = hourly ? slotStart.plusHours(1) : slotStart.plusDays(1);
            timestamps.add(slotStart.toInstant());
            List<List<Reading>> inSlot = perDevice.stream()
                    .map(rs -> between(rs, slotStart.toInstant(), slotEnd.toInstant())).toList();
            boolean hasData = inSlot.stream().anyMatch(rs -> !rs.isEmpty());
            if (slotStart.toInstant().isAfter(now) || !hasData) {
                generated.add(null);
                consumed.add(null);
                storage.add(null);
                continue;
            }
            generated.add(Stats.sig(sumKwh(inSlot, Reading::harvestedW)));
            consumed.add(Stats.sig(sumKwh(inSlot, Reading::consumptionW)));
            Double avgBattery = Stats.mean(inSlot.stream().map(rs -> Stats.mean(rs, Reading::battery)).toList());
            storage.add(avgBattery == null ? null : (double) Math.round(avgBattery));
        }
        return new History(hourly ? "hour" : "day", timestamps, generated, consumed, storage);
    }

    /** Energia (kWh) de um conjunto de dispositivos entre dois instantes, a partir das médias horárias. */
    public double energyKwh(List<DeviceConfig> configs, Instant from, Instant to, Function<Reading, Double> powerW) {
        return sumKwh(configs.stream().map(d -> between(readings.data(d).hourly(), from, to)).toList(), powerW);
    }

    private static double sumKwh(List<List<Reading>> perDevice, Function<Reading, Double> powerW) {
        double wh = 0;
        for (List<Reading> rs : perDevice) {
            for (Reading r : rs) {
                Double w = powerW.apply(r);
                if (w != null) wh += w; // média da hora (W) × 1 h
            }
        }
        return wh / 1000;
    }

    private static List<Reading> between(List<Reading> rs, Instant from, Instant to) {
        return rs.stream().filter(r -> !r.at().isBefore(from) && r.at().isBefore(to)).toList();
    }
}
