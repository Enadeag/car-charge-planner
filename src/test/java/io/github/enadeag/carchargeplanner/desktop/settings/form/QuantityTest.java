package io.github.enadeag.carchargeplanner.desktop.settings.form;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.github.enadeag.carchargeplanner.desktop.app.Units;
import org.junit.jupiter.api.Test;

class QuantityTest {

    @Test
    void metricValuesAreShownAndKeptAsTheyAre() {
        for (var quantity : Quantity.values()) {
            assertThat(quantity.shown(18, Units.METRIC)).isEqualTo(18);
            assertThat(quantity.kept(18, Units.METRIC)).isEqualTo(18);
        }
    }

    @Test
    void aDistanceIsShownInMilesAndKeptInKilometres() {
        assertThat(Quantity.DISTANCE.shown(1.609344, Units.IMPERIAL)).isEqualTo(1, within(1e-12));
        assertThat(Quantity.DISTANCE.shown(40, Units.IMPERIAL)).isEqualTo(24.85, within(0.01));
        assertThat(Quantity.DISTANCE.kept(25, Units.IMPERIAL)).isEqualTo(40.23, within(0.01));
    }

    @Test
    void consumptionIsShownInMilesPerKwhByTheReciprocal() {
        assertThat(Quantity.CONSUMPTION.shown(18, Units.IMPERIAL)).isEqualTo(3.45, within(0.01));
        assertThat(Quantity.CONSUMPTION.kept(3.45, Units.IMPERIAL)).isEqualTo(18.01, within(0.01));
        assertThat(Quantity.CONSUMPTION.kept(Quantity.CONSUMPTION.shown(17, Units.IMPERIAL), Units.IMPERIAL))
                .isEqualTo(17, within(1e-9));
    }

    @Test
    void eachUnitsHaveTheirOwnRangeAndSymbol() {
        assertThat(Quantity.DISTANCE.range(Units.METRIC)).isEqualTo(new NumberRange(0, 1000, 5, 0));
        assertThat(Quantity.DISTANCE.range(Units.IMPERIAL)).isEqualTo(new NumberRange(0, 620, 5, 0));
        assertThat(Quantity.CONSUMPTION.range(Units.METRIC)).isEqualTo(new NumberRange(5, 60, 0.5, 1));
        assertThat(Quantity.CONSUMPTION.range(Units.IMPERIAL)).isEqualTo(new NumberRange(1, 12.5, 0.1, 2));
        assertThat(Quantity.DISTANCE.unit(Units.METRIC)).isEqualTo("km");
        assertThat(Quantity.DISTANCE.unit(Units.IMPERIAL)).isEqualTo("mi");
        assertThat(Quantity.CONSUMPTION.unit(Units.METRIC)).isEqualTo("kWh/100 km");
        assertThat(Quantity.CONSUMPTION.unit(Units.IMPERIAL)).isEqualTo("mi/kWh");
    }
}
