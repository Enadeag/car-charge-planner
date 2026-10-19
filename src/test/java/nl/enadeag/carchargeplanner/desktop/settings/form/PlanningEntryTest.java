package nl.enadeag.carchargeplanner.desktop.settings.form;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import nl.enadeag.carchargeplanner.engine.config.Percent;
import nl.enadeag.carchargeplanner.engine.config.PlanningPreferences;
import nl.enadeag.carchargeplanner.engine.setting.ConfigSetting;
import nl.enadeag.carchargeplanner.engine.setting.InvalidSetting;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class PlanningEntryTest {

    @Test
    void thePageShowsTheMarginAsAPercentageAndTheDurationsInMinutes() {
        assertThat(PlanningEntry.of(PlanningPreferences.RECOMMENDED))
                .isEqualTo(new PlanningEntry(30, 80, 85, 0.2, 45, 15));
    }

    @Test
    void whatThePageShowsMakesTheSamePreferencesAgain() {
        assertThat(PlanningEntry.of(PlanningPreferences.RECOMMENDED).planning())
                .isEqualTo(PlanningPreferences.RECOMMENDED);
    }

    @Test
    void theDurationsAreRoundedToWholeMinutes() {
        assertThat(new PlanningEntry(20, 90, 50, 0, 59.5, 0.4).planning())
                .isEqualTo(new PlanningPreferences(new Percent(20), new Percent(90), 0.5, 0, Duration.ofMinutes(60),
                        Duration.ZERO));
    }

    @Test
    void aMinimumNotBelowTheTargetIsRefusedAtTheMinimum() {
        assertThatThrownBy(() -> new PlanningEntry(80, 80, 85, 0.2, 45, 15).planning())
                .isInstanceOfSatisfying(InvalidSetting.class, refused -> {
                    assertThat(refused.setting()).isEqualTo(ConfigSetting.MINIMUM_CHARGE);
                    assertThat(refused.rule()).isEqualTo(InvalidSetting.Rule.NOT_BELOW_TARGET);
                });
    }
}
