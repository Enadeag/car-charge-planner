package nl.enadeag.carchargeplanner.desktop.settings.form;

import static org.assertj.core.api.Assertions.assertThat;

import nl.enadeag.carchargeplanner.desktop.settings.form.PlanningEntry.Checked;
import nl.enadeag.carchargeplanner.desktop.text.Language;
import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.engine.config.ChargingHours;
import nl.enadeag.carchargeplanner.engine.config.Percent;
import nl.enadeag.carchargeplanner.engine.config.PlanningPreferences;
import nl.enadeag.carchargeplanner.engine.setting.ConfigSetting;
import java.time.Duration;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class PlanningEntryTest {

    private static final Messages ENGLISH = new Messages(Language.ENGLISH);

    @Test
    void thePageShowsTheMarginAsAPercentageTheDurationsInMinutesAndTheChargingHoursAsTimes() {
        assertThat(PlanningEntry.of(PlanningPreferences.RECOMMENDED))
                .isEqualTo(new PlanningEntry(30, 80, 85, 0.2, 45, 15, "07:00", "22:00"));
    }

    @Test
    void whatThePageShowsMakesTheSamePreferencesAgain() {
        assertThat(PlanningEntry.of(PlanningPreferences.RECOMMENDED).check(ENGLISH))
                .isEqualTo(new Checked.Accepted(PlanningPreferences.RECOMMENDED));
    }

    @Test
    void theDurationsAreRoundedToWholeMinutesAndTheChargingHoursMaySpanMidnight() {
        assertThat(new PlanningEntry(20, 90, 50, 0, 59.5, 0.4, " 20:30", "02:00").check(ENGLISH))
                .isEqualTo(new Checked.Accepted(new PlanningPreferences(new Percent(20), new Percent(90), 0.5, 0,
                        Duration.ofMinutes(60), Duration.ZERO,
                        new ChargingHours(LocalTime.of(20, 30), LocalTime.of(2, 0)))));
    }

    @Test
    void aMinimumNotBelowTheTargetIsRefusedAtTheMinimum() {
        assertThat(new PlanningEntry(80, 80, 85, 0.2, 45, 15, "07:00", "22:00").check(ENGLISH))
                .isEqualTo(new Checked.Refused(ConfigSetting.MINIMUM_CHARGE, "Must be below the target charge level."));
    }

    @Test
    void aChargingHourThatIsNotATimeSaysHowToTypeIt() {
        var problem = new Checked.Refused(ConfigSetting.CHARGING_HOURS,
                "Type the times as hours and minutes, such as 07:30.");

        assertThat(new PlanningEntry(30, 80, 85, 0.2, 45, 15, "7 o'clock", "22:00").check(ENGLISH)).isEqualTo(problem);
        assertThat(new PlanningEntry(30, 80, 85, 0.2, 45, 15, "07:00", "24:00").check(ENGLISH)).isEqualTo(problem);
    }

    @Test
    void chargingHoursThatEndAtTheirStartAreRefusedAtTheChargingHours() {
        assertThat(new PlanningEntry(30, 80, 85, 0.2, 45, 15, "07:00", "07:00").check(ENGLISH))
                .isEqualTo(new Checked.Refused(ConfigSetting.CHARGING_HOURS, "Choose two different times."));
    }
}
