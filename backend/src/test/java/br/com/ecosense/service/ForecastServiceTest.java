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
    void bateriaCaindoDevagarComSaldoPositivoESustentavel() {
        /* ex.: coletor que gera 2,6× o consumo e perde 0,19%/dia por autodescarga → 315 dias */
        assertThat(ForecastService.sustainableWhileFalling(315, 0.0056)).isTrue();
        /* saldo positivo, mas bateria acabando rápido: sinais contraditórios, não é sustentável */
        assertThat(ForecastService.sustainableWhileFalling(5, 0.0056)).isFalse();
        /* autonomia longa sem coleta medida (só bateria): não dá para dizer que se sustenta */
        assertThat(ForecastService.sustainableWhileFalling(212, null)).isFalse();
        assertThat(ForecastService.sustainableWhileFalling(90, -1.2)).isFalse();
    }

    @Test
    void horasComGeracaoPorDia() {
        List<Double> week = new ArrayList<>();
        for (int day = 0; day < 7; day++) {
            for (int h = 0; h < 24; h++) {
                /* sol das 8h às 17h (10 h); à noite, ruído do sensor bem abaixo de 1% do pico */
                week.add(h >= 8 && h < 18 ? 0.5 + h * 0.01 : 0.000001);
            }
        }
        assertThat(ForecastService.generatingHoursPerDay(week)).isEqualTo(10.0);
        assertThat(ForecastService.generatingHoursPerDay(List.of(0.0, 0.0))).isNull(); // menos de um dia
        assertThat(ForecastService.generatingHoursPerDay(java.util.Collections.nCopies(48, 0.0))).isEqualTo(0.0);
    }

    @Test
    void poucosPontosNaoGeramTendencia() {
        Instant now = Instant.parse("2026-11-10T12:00:00Z");
        List<Reading> rs = List.of(new Reading(now, 50d, null, null, null));
        assertThat(Stats.trend(rs, now, Reading::battery, 12)).isNull();
    }
}
