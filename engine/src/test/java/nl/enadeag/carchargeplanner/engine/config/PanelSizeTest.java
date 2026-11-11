package nl.enadeag.carchargeplanner.engine.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import nl.enadeag.carchargeplanner.engine.setting.ConfigSetting;
import nl.enadeag.carchargeplanner.engine.setting.InvalidSetting;
import java.time.Year;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class PanelSizeTest {

    private static PanelSize.Panels panels(int count, Integer wattPeak, Integer year) {
        return new PanelSize.Panels(count, wattPeak == null ? OptionalInt.empty() : OptionalInt.of(wattPeak),
                Optional.ofNullable(year).map(Year::of));
    }

    @ParameterizedTest
    @CsvSource({
        "1950, 220", "2010, 220", "2011, 245", "2013, 245", "2014, 260", "2015, 260", "2016, 280", "2017, 280",
        "2018, 300", "2019, 300", "2020, 330", "2021, 360", "2022, 380", "2023, 400", "2024, 430", "2040, 430",
    })
    void theTypicalPowerPerPanelFollowsTheYearPlaced(int year, int wattPeak) {
        assertThat(PanelSize.Panels.typicalWattPeak(Year.of(year))).isEqualTo(wattPeak);
    }

    @ParameterizedTest
    @CsvSource(nullValues = "-", value = {
        "1989, -", "1990, 220", "2026, 430", "2027, -", "-1000000000, -", "1000000000, -",
    })
    void theEstimateIsShownOnlyForAYearTheArrayMayHave(int year, Integer wattPeak) {
        var estimate = PanelSize.Panels.typicalWattPeakFor(year, Year.of(2026));

        assertThat(estimate).isEqualTo(wattPeak == null ? OptionalInt.empty() : OptionalInt.of(wattPeak));
    }

    @Test
    void thePeakPowerIsTheCountTimesThePowerPerPanel() {
        assertThat(panels(12, 300, 2021).kwp()).isEqualTo(3.6);
        assertThat(panels(12, 300, null).kwp()).isEqualTo(3.6);
    }

    @Test
    void withoutThePowerPerPanelTheTypicalOneForTheYearIsUsed() {
        var size = panels(10, null, 2021);

        assertThat(size.wattPeakUsed()).isEqualTo(360);
        assertThat(size.kwp()).isEqualTo(3.6);
    }

    @Test
    void anArrayOfPanelsGivesTheProvidersItsWorkedOutPeakPower() {
        var array = new PanelArray("", panels(10, null, 2021), 35, 0, 0.14);

        assertThat(array.kwp()).isEqualTo(3.6);
    }

    @ParameterizedTest
    @CsvSource(nullValues = "-", value = {
        "1,    50,   1990",
        "200,  1000, -",
        "1,    -,    1990",
    })
    void theEdgesOfEachRangeAreTaken(int count, Integer wattPeak, Integer year) {
        assertThat(panels(count, wattPeak, year).count()).isEqualTo(count);
    }

    @ParameterizedTest
    @CsvSource(nullValues = "-", value = {
        "0,   300,  2021, PANEL_COUNT, PANEL_COUNT_OUT_OF_RANGE",
        "201, 300,  2021, PANEL_COUNT, PANEL_COUNT_OUT_OF_RANGE",
        "10,  49,   2021, PANEL_POWER, PANEL_POWER_OUT_OF_RANGE",
        "10,  1001, 2021, PANEL_POWER, PANEL_POWER_OUT_OF_RANGE",
        "10,  300,  1989, PANEL_YEAR,  YEAR_PLACED_OUT_OF_RANGE",
        "10,  -,    -,    PANEL_YEAR,  NO_YEAR_OR_POWER",
    })
    void aValuePastItsEdgeIsRefusedAtItsField(int count, Integer wattPeak, Integer year, ConfigSetting setting,
            InvalidSetting.Rule rule) {
        assertThatThrownBy(() -> panels(count, wattPeak, year)).isInstanceOfSatisfying(InvalidSetting.class,
                refused -> {
                    assertThat(refused.setting()).isEqualTo(setting);
                    assertThat(refused.rule()).isEqualTo(rule);
                });
    }

    @Test
    void aYearAfterThisYearIsRefusedWhenEntered() {
        var thisYear = Year.of(2026);

        assertThat(panels(10, null, 2026).notPlacedAfter(thisYear)).isEqualTo(panels(10, null, 2026));
        assertThat(panels(10, 300, null).notPlacedAfter(thisYear)).isEqualTo(panels(10, 300, null));
        assertThatThrownBy(() -> panels(10, null, 2027).notPlacedAfter(thisYear))
                .isInstanceOfSatisfying(InvalidSetting.class, refused -> {
                    assertThat(refused.setting()).isEqualTo(ConfigSetting.PANEL_YEAR);
                    assertThat(refused.rule()).isEqualTo(InvalidSetting.Rule.YEAR_PLACED_OUT_OF_RANGE);
                });
    }

    @Test
    void aPeakPowerOfZeroIsRefusedAsTheSize() {
        assertThatThrownBy(() -> new PanelSize.Peak(0)).isInstanceOfSatisfying(InvalidSetting.class,
                refused -> assertThat(refused.setting()).isEqualTo(ConfigSetting.PANEL_SIZE));
    }

    @ParameterizedTest
    @CsvSource({
        "0, FLAT", "13, FLAT", "23.9, FLAT", "24, PITCHED", "35, PITCHED", "42.5, STEEP", "50, STEEP",
        "69.9, STEEP", "70, UPRIGHT", "90, UPRIGHT",
    })
    void aStoredTiltIsShownAsTheNearestChoiceTheSteeperOnATie(double tiltDeg, TiltChoice choice) {
        assertThat(TiltChoice.nearest(tiltDeg)).isEqualTo(choice);
    }

    @ParameterizedTest
    @CsvSource({
        "0, SOUTH", "-22.4, SOUTH", "-22.5, SOUTH_EAST", "22.5, SOUTH_WEST", "-45, SOUTH_EAST", "-67.5, EAST",
        "67.5, WEST", "-90, EAST", "-112.5, NORTH_EAST", "112.5, NORTH_WEST", "-157.5, NORTH", "157.5, NORTH",
        "180, NORTH", "-180, NORTH", "-170, NORTH", "170, NORTH", "130, NORTH_WEST", "-140, NORTH_EAST",
    })
    void aStoredDirectionIsShownAsTheNearestPointTheOneFurthestFromSouthOnATie(double azimuthDeg,
            CompassPoint point) {
        assertThat(CompassPoint.nearest(azimuthDeg)).isEqualTo(point);
    }

    @ParameterizedTest
    @EnumSource(TiltChoice.class)
    void eachTiltChoiceIsNearestToItself(TiltChoice choice) {
        assertThat(TiltChoice.nearest(choice.degrees())).isEqualTo(choice);
    }

    @ParameterizedTest
    @EnumSource(CompassPoint.class)
    void eachCompassPointIsNearestToItself(CompassPoint point) {
        assertThat(CompassPoint.nearest(point.azimuthDeg())).isEqualTo(point);
    }
}
