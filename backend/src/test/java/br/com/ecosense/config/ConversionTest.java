package br.com.ecosense.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

import org.junit.jupiter.api.Test;

import br.com.ecosense.config.EcoSenseProperties.Conversion;

class ConversionTest {

    @Test
    void semConversaoOsValoresPassamDireto() {
        Conversion c = Conversion.NONE;
        assertThat(c.battery(87d)).isEqualTo(87d);
        assertThat(c.consumption(0.5)).isEqualTo(0.5);
        assertThat(c.battery(null)).isNull();
    }

    @Test
    void tensaoViraPercentualLimitadoA0e100() {
        Conversion c = new Conversion(3.0, 4.2, null, null, null);
        assertThat(c.battery(3.6)).isCloseTo(50, offset(0.001));
        assertThat(c.battery(4.5)).isEqualTo(100);
        assertThat(c.battery(2.5)).isEqualTo(0);
    }

    @Test
    void escalaConverteMiliwattEInverteSinalSemFicarNegativo() {
        Conversion c = new Conversion(null, null, -0.001, 0.001, null);
        assertThat(c.consumption(-120d)).isCloseTo(0.12, offset(1e-9));
        assertThat(c.consumption(50d)).isEqualTo(0);
        assertThat(c.harvested(700d)).isCloseTo(0.7, offset(1e-9));
    }
}
