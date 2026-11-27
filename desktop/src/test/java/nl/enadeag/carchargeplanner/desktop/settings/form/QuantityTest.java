package nl.enadeag.carchargeplanner.desktop.settings.form;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import nl.enadeag.carchargeplanner.desktop.app.Units;
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
    void eachUnitsHaveTheirOwnSymbol() {
        assertThat(Quantity.DISTANCE.unit(Units.METRIC)).isEqualTo("km");
        assertThat(Quantity.DISTANCE.unit(Units.IMPERIAL)).isEqualTo("mi");
        assertThat(Quantity.CONSUMPTION.unit(Units.METRIC)).isEqualTo("kWh/100 km");
        assertThat(Quantity.CONSUMPTION.unit(Units.IMPERIAL)).isEqualTo("mi/kWh");
    }

    @Test
    void inMetricUnitsTheFieldsFollowTheEnginesLimit() {
        assertThat(Quantity.DISTANCE.range(Units.METRIC)).isEqualTo(NumberRange.of(Limit.fromTo(0, 1000), 5, 0));
        assertThat(Quantity.CONSUMPTION.range(Units.METRIC)).isEqualTo(NumberRange.of(Limit.fromTo(1, 200), 0.5, 1));
    }

    /** 1000 km is 621.37 mi; 1 to 200 kWh per 100 km is 62.14 to 0.31 mi/kWh, which turn round. */
    @Test
    void inMilesTheEnginesLimitIsConvertedAndTheArrowsStopInsideIt() {
        var distance = Quantity.DISTANCE.range(Units.IMPERIAL);
        assertThat(distance.lowest()).isEqualTo(0);
        assertThat(distance.highest()).isEqualTo(621);
        var consumption = Quantity.CONSUMPTION.range(Units.IMPERIAL);
        assertThat(consumption.lowest()).isEqualTo(0.32);
        assertThat(consumption.highest()).isEqualTo(62.13);
        assertThat(Quantity.CONSUMPTION.kept(consumption.highest(), Units.IMPERIAL)).isGreaterThanOrEqualTo(1);
        assertThat(Quantity.CONSUMPTION.kept(consumption.lowest(), Units.IMPERIAL)).isLessThanOrEqualTo(200);
        assertThat(Quantity.DISTANCE.kept(distance.highest(), Units.IMPERIAL)).isLessThanOrEqualTo(1000);
    }
}
