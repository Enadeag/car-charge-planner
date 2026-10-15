package io.github.enadeag.carchargeplanner.engine.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PlanningPreferencesTest {

    @Test
    void aMinimumThatIsNotBelowTheTargetNamesTheMinimum() {
        assertThatThrownBy(() -> new PlanningPreferences(new Percent(80), new Percent(80), 0.85, 0.2, 45, 15))
                .isInstanceOfSatisfying(InvalidSetting.class, problem -> {
                    assertThat(problem.setting()).isEqualTo(Setting.MINIMUM_CHARGE);
                    assertThat(problem.rule()).isEqualTo(InvalidSetting.Rule.NOT_BELOW_TARGET);
                });
    }

    @Test
    void aMarginAboveOneNamesTheMargin() {
        assertThatThrownBy(() -> new PlanningPreferences(new Percent(30), new Percent(80), 1.2, 0.2, 45, 15))
                .isInstanceOfSatisfying(InvalidSetting.class, problem -> {
                    assertThat(problem.setting()).isEqualTo(Setting.PLANNING_MARGIN);
                    assertThat(problem.rule()).isEqualTo(InvalidSetting.Rule.OUT_OF_RANGE);
                });
    }
}
