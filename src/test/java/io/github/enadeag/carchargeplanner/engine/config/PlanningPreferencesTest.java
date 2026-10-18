package io.github.enadeag.carchargeplanner.engine.config;

import io.github.enadeag.carchargeplanner.engine.setting.InvalidSetting;
import io.github.enadeag.carchargeplanner.engine.setting.Setting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class PlanningPreferencesTest {

    @Test
    void aMinimumThatIsNotBelowTheTargetNamesTheMinimum() {
        assertThatThrownBy(() -> new PlanningPreferences(new Percent(80), new Percent(80), 0.85, 0.2,
                Duration.ofMinutes(45), Duration.ofMinutes(15)))
                .isInstanceOfSatisfying(InvalidSetting.class, problem -> {
                    assertThat(problem.setting()).isEqualTo(Setting.MINIMUM_CHARGE);
                    assertThat(problem.rule()).isEqualTo(InvalidSetting.Rule.NOT_BELOW_TARGET);
                });
    }

    @Test
    void aMarginAboveOneNamesTheMargin() {
        assertThatThrownBy(() -> new PlanningPreferences(new Percent(30), new Percent(80), 1.2, 0.2,
                Duration.ofMinutes(45), Duration.ofMinutes(15)))
                .isInstanceOfSatisfying(InvalidSetting.class, problem -> {
                    assertThat(problem.setting()).isEqualTo(Setting.PLANNING_MARGIN);
                    assertThat(problem.rule()).isEqualTo(InvalidSetting.Rule.OUT_OF_RANGE);
                });
    }

    @Test
    void durationsTheConfigFileCannotHoldAsWholeMinutesAreRefused() {
        assertThatThrownBy(() -> new PlanningPreferences(new Percent(30), new Percent(80), 0.85, 0.2,
                Duration.ofSeconds(45 * 60 + 30), Duration.ofMinutes(15)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("minWindow");
        assertThatThrownBy(() -> new PlanningPreferences(new Percent(30), new Percent(80), 0.85, 0.2,
                Duration.ofMinutes(45), Duration.ofMinutes(15).plusNanos(1)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("notifyLead");
    }
}
