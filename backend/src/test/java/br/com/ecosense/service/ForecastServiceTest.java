package br.com.ecosense.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class ForecastServiceTest {

    @Test
    void estacoesDoHemisferioSul() {
        assertThat(ForecastService.season(LocalDate.of(2026, 11, 10))).isEqualTo("Primavera");
        assertThat(ForecastService.season(LocalDate.of(2026, 1, 15))).isEqualTo("Verão");
        assertThat(ForecastService.season(LocalDate.of(2026, 12, 21))).isEqualTo("Verão");
        assertThat(ForecastService.season(LocalDate.of(2026, 4, 1))).isEqualTo("Outono");
        assertThat(ForecastService.season(LocalDate.of(2026, 7, 1))).isEqualTo("Inverno");
    }

    @Test
    void tendenciaLinearDaBateria() {
        Instant now = Instant.parse("2026-11-10T12:00:00Z");
        List<Reading> rs = new ArrayList<>();
        for (int h = ForecastService.TREND_HOURS; h >= 0; h--) {
            /* cai 4 pontos por dia ao longo da janela de 7 dias, terminando em 10% */
            rs.add(new Reading(now.minusSeconds(h * 3600L), 10 + 4 * h / 24.0, null, null, null));
        }
        Stats.Trend t = Stats.trend(rs, now, Reading::battery, 12);

        assertThat(t.slopePerDay()).isCloseTo(-4, org.assertj.core.data.Offset.offset(0.001));
        assertThat(t.residualStd()).isCloseTo(0, org.assertj.core.data.Offset.offset(0.001));
        assertThat(ForecastService.confidence(t)).isEqualTo(99);
    }

    @Test
    void poucosPontosNaoGeramTendencia() {
        Instant now = Instant.parse("2026-11-10T12:00:00Z");
        List<Reading> rs = List.of(new Reading(now, 50d, null, null, null));
        assertThat(Stats.trend(rs, now, Reading::battery, 12)).isNull();
    }
}
