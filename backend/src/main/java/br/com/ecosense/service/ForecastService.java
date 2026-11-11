package br.com.ecosense.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import br.com.ecosense.api.ApiDtos.ForecastItem;
import br.com.ecosense.api.ApiDtos.ForecastSeries;
import br.com.ecosense.api.ApiDtos.Forecasts;
import br.com.ecosense.api.ApiDtos.Series;
import br.com.ecosense.api.ApiException;
import br.com.ecosense.config.EcoSenseProperties;
import br.com.ecosense.config.EcoSenseProperties.DeviceConfig;
import br.com.ecosense.service.ReadingService.DeviceData;

/**
 * Previsão de autonomia da bateria.
 * Modelo simples e explicável: tendência linear da bateria nas últimas {@value #TREND_HOURS} h.
 * Queda por dia → dias até zerar; estável ou subindo → sustentável. A confiança cai quanto mais
 * os pontos fogem da reta e quanto menos pontos houver. Pode ser trocado por um modelo melhor
 * sem mudar o contrato da API.
 */
@Service
public class ForecastService {

    static final int TREND_HOURS = 72;
    static final int MIN_POINTS = 12;
    /** queda menor que isso (pontos % por dia) conta como estável */
    static final double SUSTAINABLE_SLOPE = -0.05;

    private final ReadingService readings;
    private final DeviceService devices;
    private final EcoSenseProperties props;
    private final Clock clock;

    public ForecastService(ReadingService readings, DeviceService devices, EcoSenseProperties props, Clock clock) {
        this.readings = readings;
        this.devices = devices;
        this.props = props;
        this.clock = clock;
    }

    public Forecasts forecasts() {
        Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        List<ForecastItem> items = devices.configs().stream().map(this::item).toList();
        return new Forecasts(season(LocalDate.ofInstant(now, props.zone())), now, items);
    }

    public ForecastItem item(DeviceConfig d) {
        DeviceData data = readings.data(d);
        Stats.Trend trend = trend(data);
        Double battery = data.latest() == null ? null : data.latest().battery();
        if (!devices.isOnline(d, data) || trend == null || battery == null) {
            return new ForecastItem(d.id(), null, false, null, d.sunHours());
        }
        int confidence = confidence(trend);
        if (trend.slopePerDay() >= SUSTAINABLE_SLOPE) {
            return new ForecastItem(d.id(), null, true, confidence, d.sunHours());
        }
        int daysLeft = (int) Math.max(1, Math.ceil(battery / -trend.slopePerDay()));
        return new ForecastItem(d.id(), daysLeft, false, confidence, d.sunHours());
    }

    /** range: 24h (12 h passadas + 24 futuras, passo 1 h) ou 7d (3 dias passados + 7 futuros, passo 1 dia). */
    public ForecastSeries series(String id, String range) {
        boolean hourly;
        if ("24h".equals(range)) hourly = true;
        else if ("7d".equals(range)) hourly = false;
        else throw ApiException.badRequest("Período de previsão inválido. Use 24h ou 7d.");

        DeviceConfig d = devices.config(id);
        DeviceData data = readings.data(d);
        Instant now = clock.instant();
        ChronoUnit unit = hourly ? ChronoUnit.HOURS : ChronoUnit.DAYS;
        int past = hourly ? 12 : 3, future = hourly ? 24 : 7;
        ZonedDateTime base = now.atZone(props.zone()).truncatedTo(unit);

        Map<Instant, List<Reading>> buckets = data.hourly().stream().collect(Collectors.groupingBy(
                r -> r.at().atZone(props.zone()).truncatedTo(unit).toInstant()));
        List<Reading> lastWeek = DeviceService.since(data.hourly(), now.minus(Duration.ofDays(7)));
        Double weekAvgW = Stats.mean(lastWeek, Reading::consumptionW);

        Stats.Trend trend = devices.isOnline(d, data) ? trend(data) : null;
        double slope = trend == null ? 0 : trend.slopePerDay();
        Double current = data.latest() == null ? null : data.latest().battery();

        List<Instant> timestamps = new ArrayList<>();
        List<Double> consMeasured = new ArrayList<>(), consPredicted = new ArrayList<>();
        List<Double> battMeasured = new ArrayList<>(), battPredicted = new ArrayList<>();
        for (int i = 0; i < past + 1 + future; i++) {
            ZonedDateTime slot = base.plus(i - past, unit);
            Instant ts = slot.toInstant();
            timestamps.add(ts);
            List<Reading> inSlot = buckets.getOrDefault(ts, List.of());

            /* consumo em Wh por passo: média em W × horas do passo */
            double hours = hourly ? 1 : 24;
            Double measuredW = i <= past ? Stats.mean(inSlot, Reading::consumptionW) : null;
            consMeasured.add(measuredW == null ? null : Stats.round(measuredW * hours, 1));
            Double predictedW = hourly ? sameHourAverage(lastWeek, slot.getHour(), weekAvgW) : weekAvgW;
            consPredicted.add(predictedW == null ? null : Stats.round(predictedW * hours, 1));

            Double measuredBatt = i <= past ? Stats.mean(inSlot, Reading::battery) : null;
            battMeasured.add(measuredBatt == null ? null : (double) Math.round(measuredBatt));
            if (i >= past && current != null) {
                double days = (i - past) * (hourly ? 1 / 24.0 : 1);
                battPredicted.add((double) Math.round(Stats.clamp(current + slope * days, 0, 100)));
            } else {
                battPredicted.add(null);
            }
        }
        return new ForecastSeries(hourly ? "hour" : "day", timestamps, past,
                new Series(consMeasured, consPredicted), new Series(battMeasured, battPredicted));
    }

    private Stats.Trend trend(DeviceData data) {
        Instant now = clock.instant();
        List<Reading> window = DeviceService.since(data.hourly(), now.minus(Duration.ofHours(TREND_HOURS)));
        return Stats.trend(window, now, Reading::battery, MIN_POINTS);
    }

    /** 99 com pontos bem alinhados e janela completa; desce com ruído e com poucos dados. Mínimo 40. */
    static int confidence(Stats.Trend t) {
        double coverage = Math.min(1, t.points() / (double) TREND_HOURS);
        double value = 100 - 4 * t.residualStd() - 25 * (1 - coverage);
        return (int) Math.round(Stats.clamp(value, 40, 99));
    }

    /** Consumo médio (W) na mesma hora do dia ao longo da última semana; fallback se não houver dados. */
    private Double sameHourAverage(List<Reading> readings, int hour, Double fallback) {
        Double avg = Stats.mean(readings.stream()
                .filter(r -> r.at().atZone(props.zone()).getHour() == hour)
                .map(Reading::consumptionW).toList());
        return avg != null ? avg : fallback;
    }

    /** Estação do ano no hemisfério sul (Brasil). */
    static String season(LocalDate date) {
        MonthDay md = MonthDay.from(date);
        if (!md.isBefore(MonthDay.of(9, 22)) && md.isBefore(MonthDay.of(12, 21))) return "Primavera";
        if (!md.isBefore(MonthDay.of(3, 20)) && md.isBefore(MonthDay.of(6, 21))) return "Outono";
        if (!md.isBefore(MonthDay.of(6, 21)) && md.isBefore(MonthDay.of(9, 22))) return "Inverno";
        return "Verão";
    }
}
