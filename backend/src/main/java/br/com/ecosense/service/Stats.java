package br.com.ecosense.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

final class Stats {

    private Stats() {}

    static Double mean(Collection<Double> values) {
        double sum = 0;
        int n = 0;
        for (Double v : values) {
            if (v == null) continue;
            sum += v;
            n++;
        }
        return n == 0 ? null : sum / n;
    }

    static <T> Double mean(List<T> items, Function<T, Double> value) {
        return mean(items.stream().map(value).toList());
    }

    static double round(double value, int decimals) {
        double factor = Math.pow(10, decimals);
        return Math.round(value * factor) / factor;
    }

    static Double round(Double value, int decimals) {
        return value == null ? null : round(value.doubleValue(), decimals);
    }

    static double sig(double value) {
        if (value == 0 || !Double.isFinite(value)) return value == 0 ? 0 : value;
        return new java.math.BigDecimal(value).round(new java.math.MathContext(4)).doubleValue();
    }

    static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    record Trend(double slopePerDay, double residualStd, int points) {}

    static Trend trend(List<Reading> readings, Instant reference, Function<Reading, Double> value, int minPoints) {
        List<double[]> pts = readings.stream()
                .filter(r -> value.apply(r) != null)
                .map(r -> new double[] {
                        Duration.between(reference, r.at()).toMinutes() / 1440.0, value.apply(r) })
                .toList();
        int n = pts.size();
        if (n < minPoints) return null;
        double mx = pts.stream().mapToDouble(p -> p[0]).average().orElse(0);
        double my = pts.stream().mapToDouble(p -> p[1]).average().orElse(0);
        double sxx = 0, sxy = 0;
        for (double[] p : pts) {
            sxx += (p[0] - mx) * (p[0] - mx);
            sxy += (p[0] - mx) * (p[1] - my);
        }
        if (sxx == 0) return null;
        double slope = sxy / sxx, intercept = my - slope * mx;
        double sse = 0;
        for (double[] p : pts) {
            double r = p[1] - (intercept + slope * p[0]);
            sse += r * r;
        }
        return new Trend(slope, Math.sqrt(sse / n), n);
    }

    static <T> long countNonNull(List<T> items, Function<T, ?> value) {
        return items.stream().map(value).filter(Objects::nonNull).count();
    }
}
