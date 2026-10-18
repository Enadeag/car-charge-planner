package io.github.enadeag.carchargeplanner.desktop.settings.form;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.github.enadeag.carchargeplanner.desktop.app.Units;
import org.junit.jupiter.api.Test;

class UnitValueTest {

    private final UnitValue consumption = new UnitValue(Quantity.CONSUMPTION);
    private final UnitValue distance = new UnitValue(Quantity.DISTANCE);

    @Test
    void aNewValueShowsInMetricUnits() {
        assertThat(consumption.units()).isEqualTo(Units.METRIC);
        assertThat(consumption.range()).isEqualTo(new NumberRange(5, 60, 0.5, 1));
        assertThat(consumption.unit()).isEqualTo("kWh/100 km");
    }

    @Test
    void aLoadedValueIsShownInTheUnitsAndIsNotChanged() {
        assertThat(consumption.load(18)).isEqualTo(18);
        assertThat(consumption.isChanged(18)).isFalse();
        assertThat(consumption.read(18)).isEqualTo(18);
        assertThat(consumption.shownEdit()).isEmpty();
    }

    @Test
    void otherUnitsChangeTheRangeAndSymbolAndShowTheLoadedValueInThem() {
        consumption.load(18);
        consumption.showIn(Units.IMPERIAL);

        assertThat(consumption.units()).isEqualTo(Units.IMPERIAL);
        assertThat(consumption.range()).isEqualTo(new NumberRange(1, 12.5, 0.1, 2));
        assertThat(consumption.unit()).isEqualTo("mi/kWh");
        assertThat(consumption.shownLoaded()).isEqualTo(3.45, within(0.01));
    }

    @Test
    void aValueShownAsItWasAtTheFieldsDecimalsStandsForTheExactKeptValue() {
        consumption.load(18);
        consumption.showIn(Units.IMPERIAL);

        assertThat(consumption.isChanged(3.45)).isFalse();
        assertThat(consumption.read(3.45)).isEqualTo(18);
        assertThat(consumption.shownEdit()).isEmpty();
    }

    @Test
    void aTypedValueIsKeptMetricAndCountsAsChanged() {
        distance.load(40);
        distance.showIn(Units.IMPERIAL);

        assertThat(distance.isChanged(25)).as("25 mi is how 40 km shows without decimals").isFalse();
        assertThat(distance.isChanged(26)).isTrue();
        assertThat(distance.read(26)).isEqualTo(26 * 1.609344, within(1e-9));
        assertThat(distance.shownEdit()).hasValueCloseTo(26.0, within(1e-9));
    }

    @Test
    void isChangedHoldsNothingButReadDoes() {
        distance.load(40);

        assertThat(distance.isChanged(50)).isTrue();
        assertThat(distance.shownEdit()).isEmpty();

        distance.read(50);
        assertThat(distance.shownEdit()).hasValue(50);
        assertThat(distance.isChanged(50)).isTrue();
    }

    @Test
    void anEditedValueComesBackAsTypedWhenTheUnitsSwitchBack() {
        consumption.load(18);
        consumption.read(20);
        consumption.showIn(Units.IMPERIAL);

        var shown = consumption.shownEdit().orElseThrow();
        assertThat(shown).isEqualTo(3.11, within(0.01));
        assertThat(consumption.read(Math.round(shown * 100) / 100.0)).as("rounded as the field shows it")
                .isEqualTo(20);

        consumption.showIn(Units.METRIC);
        assertThat(consumption.shownLoaded()).isEqualTo(18);
        assertThat(consumption.shownEdit()).hasValue(20);
    }

    @Test
    void aSetValueIsHeldAsIfTheOwnerEnteredIt() {
        consumption.load(18);
        consumption.showIn(Units.IMPERIAL);

        assertThat(consumption.set(17)).isEqualTo(Quantity.CONSUMPTION.shown(17, Units.IMPERIAL));
        assertThat(consumption.isChanged(Quantity.CONSUMPTION.shown(17, Units.IMPERIAL))).isTrue();
        assertThat(consumption.read(Quantity.CONSUMPTION.shown(17, Units.IMPERIAL))).isEqualTo(17);
    }

    @Test
    void loadingAgainMakesTheNewValueTheOneNotEdited() {
        distance.load(40);
        distance.read(50);

        assertThat(distance.load(60)).isEqualTo(60);
        assertThat(distance.shownEdit()).isEmpty();
        assertThat(distance.isChanged(60)).isFalse();
    }
}
