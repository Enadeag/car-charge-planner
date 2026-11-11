package nl.enadeag.carchargeplanner.desktop.settings.form;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import nl.enadeag.carchargeplanner.desktop.settings.form.PanelArrayEntry.Checked;
import nl.enadeag.carchargeplanner.desktop.text.Language;
import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.engine.config.PanelArray;
import nl.enadeag.carchargeplanner.engine.config.PanelSize;
import nl.enadeag.carchargeplanner.engine.setting.ConfigSetting;
import java.time.Year;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PanelArrayEntryTest {

    private static final Messages ENGLISH = new Messages(Language.ENGLISH);
    private static final Year THIS_YEAR = Year.of(2026);
    private static final OptionalDouble SOUTH = OptionalDouble.of(0);
    private static final OptionalDouble PITCHED = OptionalDouble.of(35);

    private static PanelArrayEntry typed(String count, String year, String wattPeak) {
        return new PanelArrayEntry("", count, year, wattPeak, PITCHED, SOUTH, 14, Optional.empty());
    }

    private static PanelArray panels(int count, OptionalInt wattPeak, Optional<Year> year) {
        return new PanelArray("", new PanelSize.Panels(count, wattPeak, year), 35, 0, 0.14);
    }

    @Test
    void anArrayOfPanelsShowsItsCountYearAndPowerAsTyped() {
        var array = new PanelArray("east roof", new PanelSize.Panels(12, OptionalInt.empty(),
                Optional.of(Year.of(2021))), 30, -90, 0.12);

        assertThat(PanelArrayEntry.of(array)).isEqualTo(new PanelArrayEntry("east roof", "12", "2021", "",
                OptionalDouble.of(30), OptionalDouble.of(-90), 12, Optional.empty()));
        assertThat(PanelArrayEntry.of(array).check(THIS_YEAR, ENGLISH)).isEqualTo(new Checked.Accepted(array));
    }

    @Test
    void anArrayStoredByItsPeakPowerShowsNoPanelsAndKeepsItWhileTheyStayEmpty() {
        var stored = new PanelArray("garage", 4.2, 15, 45, 0.14);
        var entry = PanelArrayEntry.of(stored);

        assertThat(entry.count()).isEmpty();
        assertThat(entry.keepsThePeakPower()).isTrue();
        assertThat(entry.check(THIS_YEAR, ENGLISH)).isEqualTo(new Checked.Accepted(stored));
        assertThat(entry.together(THIS_YEAR)).isEmpty();
    }

    @Test
    void typingTheCountReplacesTheStoredPeakPower() {
        var entry = PanelArrayEntry.of(new PanelArray("", 4.2, 35, 0, 0.14));
        var typed = new PanelArrayEntry("", "10", "2021", "", entry.tiltDeg(), entry.azimuthDeg(), 14, entry.kept());

        assertThat(typed.keepsThePeakPower()).isFalse();
        assertThat(typed.check(THIS_YEAR, ENGLISH))
                .isEqualTo(new Checked.Accepted(panels(10, OptionalInt.empty(), Optional.of(Year.of(2021)))));
    }

    @Test
    void aYearWithoutTheCountIsRefusedAtTheCountForAStoredPeakPowerToo() {
        var entry = PanelArrayEntry.of(new PanelArray("", 4.2, 35, 0, 0.14));
        var typed = new PanelArrayEntry("", "", "2021", "", entry.tiltDeg(), entry.azimuthDeg(), 14, entry.kept());

        assertThat(typed.check(THIS_YEAR, ENGLISH)).isEqualTo(new Checked.Refused(ConfigSetting.PANEL_COUNT,
                "Type the number of panels: a whole number from 1 to 200."));
    }

    @Test
    void anEmptyPowerIsLeftOutAndTheTypicalOneForTheYearIsCountedOn() {
        var entry = typed("10", "2021", " ");

        assertThat(entry.check(THIS_YEAR, ENGLISH))
                .isEqualTo(new Checked.Accepted(panels(10, OptionalInt.empty(), Optional.of(Year.of(2021)))));
        assertThat(entry.typicalWattPeak(THIS_YEAR)).hasValue(360);
        assertThat(entry.together(THIS_YEAR)).hasValue(3.6);
    }

    @Test
    void aTypedPowerReplacesTheEstimateAndTheYearMayThenStayEmpty() {
        var entry = typed("12", "", "300");

        assertThat(entry.check(THIS_YEAR, ENGLISH))
                .isEqualTo(new Checked.Accepted(panels(12, OptionalInt.of(300), Optional.empty())));
        assertThat(entry.together(THIS_YEAR)).hasValue(3.6);
        assertThat(entry.typicalWattPeak(THIS_YEAR)).isEmpty();
    }

    @ParameterizedTest
    @CsvSource(nullValues = "-", value = {
        "'',   2021, '',   PANEL_COUNT, Type the number of panels: a whole number from 1 to 200.",
        "1.5,  2021, '',   PANEL_COUNT, Type the number of panels: a whole number from 1 to 200.",
        "201,  2021, '',   PANEL_COUNT, Type the number of panels: a whole number from 1 to 200.",
        "10,   '',   '',   PANEL_YEAR,  'Type the year placed, or the power per panel.'",
        "10,   1989, '',   PANEL_YEAR,  Type the year placed: from 1990 up to and including this year.",
        "10,   2027, '',   PANEL_YEAR,  Type the year placed: from 1990 up to and including this year.",
        "10,   twee, '',   PANEL_YEAR,  Type the year placed: from 1990 up to and including this year.",
        "10,   2021, 49,   PANEL_POWER, Type the power per panel in Wp: a whole number from 50 to 1000.",
        "10,   2021, 1001, PANEL_POWER, Type the power per panel in Wp: a whole number from 50 to 1000.",
        "10,   2021, '3.5', PANEL_POWER, Type the power per panel in Wp: a whole number from 50 to 1000.",
    })
    void aWrongOrMissingValueIsRefusedAtItsFieldWithItsOwnSentence(String count, String year, String wattPeak,
            ConfigSetting setting, String problem) {
        assertThat(typed(count, year, wattPeak).check(THIS_YEAR, ENGLISH))
                .isEqualTo(new Checked.Refused(setting, problem));
        assertThat(typed(count, year, wattPeak).together(THIS_YEAR)).isEmpty();
    }

    @Test
    void aNewArrayAsksForADirectionAndATiltAfterTheCount() {
        var empty = PanelArrayEntry.empty();
        var counted = new PanelArrayEntry("", "10", "2021", "", OptionalDouble.empty(), OptionalDouble.empty(),
                empty.lossesPct(), Optional.empty());
        var directed = new PanelArrayEntry("", "10", "2021", "", OptionalDouble.empty(), SOUTH, empty.lossesPct(),
                Optional.empty());

        assertThat(empty.check(THIS_YEAR, ENGLISH)).isInstanceOfSatisfying(Checked.Refused.class,
                refused -> assertThat(refused.setting()).isEqualTo(ConfigSetting.PANEL_COUNT));
        assertThat(counted.check(THIS_YEAR, ENGLISH))
                .isEqualTo(new Checked.Refused(ConfigSetting.PANEL_DIRECTION, "Choose one."));
        assertThat(directed.check(THIS_YEAR, ENGLISH))
                .isEqualTo(new Checked.Refused(ConfigSetting.PANEL_TILT, "Choose one."));
    }

    @Test
    void aNewArrayStartsWithTheTypicalLossesOnly() {
        var empty = PanelArrayEntry.empty();

        assertThat(empty.count() + empty.yearPlaced() + empty.wattPeak() + empty.name()).isEmpty();
        assertThat(empty.tiltDeg()).isEmpty();
        assertThat(empty.azimuthDeg()).isEmpty();
        assertThat(empty.kept()).isEmpty();
        assertThat(empty.lossesPct()).isCloseTo(14, within(1e-9));
    }

    @Test
    void theEstimateNeedsAYearTheArrayMayHave() {
        assertThat(typed("10", "2027", "").typicalWattPeak(THIS_YEAR)).isEmpty();
        assertThat(typed("10", "1989", "").typicalWattPeak(THIS_YEAR)).isEmpty();
        assertThat(typed("10", "1990", "").typicalWattPeak(THIS_YEAR)).hasValue(220);
        assertThat(typed("10", "2026", "").typicalWattPeak(THIS_YEAR)).hasValue(430);
    }

    @Test
    void theLossesAreAShareOfOneInTheArray() {
        var entry = new PanelArrayEntry("", "10", "", "400", PITCHED, SOUTH, 12, Optional.empty());

        assertThat(entry.check(THIS_YEAR, ENGLISH)).isInstanceOfSatisfying(Checked.Accepted.class,
                accepted -> assertThat(accepted.array().systemLosses()).isEqualTo(0.12));
    }
}
