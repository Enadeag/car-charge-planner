package nl.enadeag.carchargeplanner.desktop.settings.form;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import nl.enadeag.carchargeplanner.desktop.app.Units;
import nl.enadeag.carchargeplanner.desktop.text.Language;
import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.engine.setting.ConfigSetting;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class LimitTest {

    private static final Messages ENGLISH = new Messages(Language.ENGLISH);
    private static final Messages DUTCH = new Messages(Language.DUTCH);

    @Test
    void eachShapeHasItsOwnText() {
        assertThat(Limit.fromTo(1, 1000).text(1, ENGLISH)).isEqualTo("Type a number from 1 to 1000.");
        assertThat(Limit.atLeast(0).text(2, ENGLISH)).isEqualTo("Type a number of 0 or more.");
        assertThat(Limit.moreThan(0).text(0, ENGLISH)).isEqualTo("Type a number more than 0.");
        assertThat(Limit.of(ConfigSetting.PLANNING_MARGIN).text(0, ENGLISH))
                .isEqualTo("Type a number more than 0 and at most 100.");
        assertThat(Limit.of(ConfigSetting.PANEL_LOSSES).text(0, ENGLISH))
                .isEqualTo("Type a number of 0 or more and below 100.");
    }

    @Test
    void theEndsAreWrittenInTheOwnersLanguage() {
        assertThat(Limit.fromTo(0.01, 1).scaled(100).text(0, DUTCH)).isEqualTo("Typ een getal van 1 tot en met 100.");
        assertThat(Limit.fromTo(-90, 90).text(4, DUTCH)).isEqualTo("Typ een getal van -90 tot en met 90.");
        assertThat(Limit.fromTo(0.32, 62.13).text(2, DUTCH)).isEqualTo("Typ een getal van 0,32 tot en met 62,13.");
    }

    /** 1000 km is 621.37 mi: the text names 621, which is accepted, not 621.4, which is not. */
    @Test
    void anEndConvertedToOtherUnitsIsNamedRoundedInward() {
        var miles = Limit.of(ConfigSetting.CAR_DAILY_DISTANCE).converted(km -> Units.IMPERIAL.distance(km));
        assertThat(miles.text(0, ENGLISH)).isEqualTo("Type a number from 0 to 621.");
        assertThat(miles.text(1, ENGLISH)).isEqualTo("Type a number from 0 to 621.3.");
        var milesPerKwh = Limit.of(ConfigSetting.CAR_CONSUMPTION)
                .converted(kwh -> Quantity.CONSUMPTION.shown(kwh, Units.IMPERIAL));
        assertThat(milesPerKwh.text(2, ENGLISH)).isEqualTo("Type a number from 0.32 to 62.13.");
    }

    @Test
    void theArrowsStopAtTheEndsTheFieldCanShowAndOneStepInsideAnEndThatIsNotAllowed() {
        assertThat(Limit.of(ConfigSetting.PLANNING_MARGIN).lowest(0)).isEqualTo(1);
        assertThat(Limit.of(ConfigSetting.PLANNING_MARGIN).highest(0)).isEqualTo(100);
        assertThat(Limit.of(ConfigSetting.PANEL_LOSSES).lowest(0)).isEqualTo(0);
        assertThat(Limit.of(ConfigSetting.PANEL_LOSSES).highest(0)).isEqualTo(99);
        assertThat(Limit.of(ConfigSetting.PANEL_LOSSES).highest(1)).isEqualTo(99.9);
        assertThat(Limit.of(ConfigSetting.MINIMUM_WINDOW).lowest(0)).isEqualTo(1);
        assertThat(Limit.of(ConfigSetting.MINIMUM_WINDOW).highest(0)).isEqualTo(Double.POSITIVE_INFINITY);
    }

    /** The household use is typed per day, the share settings in %; the engine keeps kW and shares of 1. */
    @Test
    void aLimitIsInTheUnitItsFieldShows() {
        assertThat(Limit.of(ConfigSetting.HOUSE_BASELOAD)).isEqualTo(Limit.atLeast(0));
        assertThat(Limit.of(ConfigSetting.CHARGER_EFFICIENCY)).isEqualTo(Limit.fromTo(1, 100));
        assertThat(Limit.of(ConfigSetting.CAR_BATTERY)).isEqualTo(Limit.fromTo(1, 1000));
        assertThat(Limit.of(ConfigSetting.CAR_TRIP_SURCHARGE)).isEqualTo(Limit.fromTo(0, 100));
        assertThat(Limit.of(ConfigSetting.IMPORT_TOLERANCE)).isEqualTo(Limit.atLeast(0));
    }

    @ParameterizedTest
    @EnumSource(value = ConfigSetting.class, names = {"PANEL_COUNT", "PANEL_TILT", "NOTIFY_LEAD", "CHARGING_HOURS"})
    void aSettingThatIsNotATypedNumberHasNoLimit(ConfigSetting setting) {
        assertThatIllegalArgumentException().isThrownBy(() -> Limit.of(setting));
    }

    @Test
    void endsThatMakeNoLimitAreRefused() {
        assertThatIllegalArgumentException().isThrownBy(() -> Limit.fromTo(5, 5));
        assertThatIllegalArgumentException().isThrownBy(() -> Limit.atLeast(Double.NEGATIVE_INFINITY));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Limit(0, true, Double.POSITIVE_INFINITY, true));
    }
}
