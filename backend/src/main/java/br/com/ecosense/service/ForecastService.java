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
 * Modelo simples e explicável, em duas partes:
 * <ol>
 *   <li>Tendência linear da bateria nas últimas {@value #TREND_HOURS} h (7 dias, para diluir o ciclo
 *       dia/noite e a sorte de um dia ensolarado). Queda por dia → dias até zerar.</li>
 *   <li>Saldo energético no mesmo período (coleta − consumo, quando o canal mede os dois). Bateria
 *       estável ou subindo só conta como sustentável se o saldo não for negativo; senão o
 *       dispositivo fica com saldo negativo (deficit), sem número de dias, pois a capacidade da
 *       bateria em Wh não é conhecida.</li>
 *   <li>Bateria caindo devagar (autonomia acima de {@value #LONG_AUTONOMY_DAYS} dias) com saldo positivo
 *       também conta como sustentável: a queda é autodescarga/perdas, não falta de energia.</li>
 * </ol>
 * A confiança cai quanto mais os pontos fogem da reta, quanto menos pontos houver e quando a
 * tendência e o saldo apontam em direções opostas. Pode ser trocado por um modelo melhor sem
 * mudar o contrato da API.
 */
@Service
public class ForecastService {

    static final int TREND_HOURS = 7 * 24;
    static final int MIN_POINTS = 12;
    /** queda menor que isso (pontos % por dia) conta como estável */
    static final double SUSTAINABLE_SLOPE = -0.05;
    static final int MIN_CONFIDENCE = 40;
    /** bateria caindo devagar (autodescarga, perdas) com coleta maior que o consumo: acima disso, sustentável */
    static final int LONG_AUTONOMY_DAYS = 30;
    /** hora "com geração": coleta acima desta fração do pico da janela (ignora ruído do sensor à noite) */
    static final double GENERATING_FRACTION = 0.01;
    /** desconto na confiança quando bateria e saldo energético discordam */
    static final int DISAGREEMENT_PENALTY = 20;

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
        Double sunHours = sunHours(d, data);
        if (!devices.isOnline(d, data) || trend == null || battery == null) {
            return new ForecastItem(d.id(), null, false, false, null, null, sunHours);
        }
        Double balance = balanceWhDay(d, data);
        boolean falling = trend.slopePerDay() < SUSTAINABLE_SLOPE;
        boolean negativeBalance = balance != null && balance < 0;
        /* bateria subindo com saldo negativo (ou caindo com saldo positivo): sinais contraditórios */
        boolean disagree = balance != null && falling != negativeBalance;
        int confidence = Math.max(MIN_CONFIDENCE, confidence(trend) - (disagree ? DISAGREEMENT_PENALTY : 0));
        Double balanceOut = balance == null ? null : Stats.sig(balance);
        if (falling) {
            int daysLeft = (int) Math.max(1, Math.ceil(battery / -trend.slopePerDay()));
            return new ForecastItem(d.id(), daysLeft, sustainableWhileFalling(daysLeft, balance), false,
                    balanceOut, confidence, sunHours);
        }
        return new ForecastItem(d.id(), null, !negativeBalance, negativeBalance, balanceOut, confidence, sunHours);
    }

    /**
     * Bateria caindo, mas coleta maior que o consumo e autonomia longa: a queda vem de autodescarga ou
     * perdas, não de falta de energia, então o dispositivo se sustenta. Sem saldo medido não há como saber.
     */
    static boolean sustainableWhileFalling(int daysLeft, Double balanceWhDay) {
        return balanceWhDay != null && balanceWhDay > 0 && daysLeft > LONG_AUTONOMY_DAYS;
    }

    /**
     * Horas de sol por dia: a cadastrada no dispositivo ou, sem ela, as horas com geração medidas na
     * janela da tendência (7 dias). Null se o canal não mede coleta ou há menos de um dia de leituras.
     */
    private Double sunHours(DeviceConfig d, DeviceData data) {
        if (d.sunHours() != null) return d.sunHours();
        if (!d.fields().measuresHarvested()) return null;
        List<Double> harvested = DeviceService.since(data.hourly(), clock.instant().minus(Duration.ofHours(TREND_HOURS)))
                .stream().map(Reading::harvestedW).filter(w -> w != null).toList();
        return generatingHoursPerDay(harvested);
    }

    /** Média horária de coleta (W) → horas por dia com geração. Null com menos de 24 leituras. */
    static Double generatingHoursPerDay(List<Double> hourlyHarvestedW) {
        if (hourlyHarvestedW.size() < 24) return null;
        double peak = hourlyHarvestedW.stream().mapToDouble(Double::doubleValue).max().orElse(0);
        if (peak <= 0) return 0d;
        long generating = hourlyHarvestedW.stream().filter(w -> w > peak * GENERATING_FRACTION).count();
        return Stats.round(generating * 24.0 / hourlyHarvestedW.size(), 1);
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
        /* saldo negativo: não projeta a bateria subindo, mesmo que a reta suba */
        Double balance = balanceWhDay(d, data);
        if (balance != null && balance < 0) slope = Math.min(slope, 0);
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
            consMeasured.add(measuredW == null ? null : Stats.sig(measuredW * hours));
            Double predictedW = hourly ? sameHourAverage(lastWeek, slot.getHour(), weekAvgW) : weekAvgW;
            consPredicted.add(predictedW == null ? null : Stats.sig(predictedW * hours));

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

    /**
     * Saldo energético médio (Wh/dia) na janela da tendência: coleta − consumo.
     * Null se o canal não mede as duas grandezas ou se não há leituras delas.
     */
    private Double balanceWhDay(DeviceConfig d, DeviceData data) {
        if (!d.fields().measuresConsumption() || !d.fields().measuresHarvested()) return null;
        List<Reading> window = DeviceService.since(data.hourly(), clock.instant().minus(Duration.ofHours(TREND_HOURS)));
        Double harvestedW = Stats.mean(window, Reading::harvestedW);
        Double consumptionW = Stats.mean(window, Reading::consumptionW);
        if (harvestedW == null || consumptionW == null) return null;
        return (harvestedW - consumptionW) * 24;
    }

    /** 99 com pontos bem alinhados e janela completa; desce com ruído e com poucos dados. Mínimo 40. */
    static int confidence(Stats.Trend t) {
        double coverage = Math.min(1, t.points() / (double) TREND_HOURS);
        double value = 100 - 4 * t.residualStd() - 25 * (1 - coverage);
        return (int) Math.round(Stats.clamp(value, MIN_CONFIDENCE, 99));
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
