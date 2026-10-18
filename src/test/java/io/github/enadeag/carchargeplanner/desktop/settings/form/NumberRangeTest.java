package io.github.enadeag.carchargeplanner.desktop.settings.form;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class NumberRangeTest {

    private static final NumberRange PERCENT = new NumberRange(0, 100, 5, 0);

    @Test
    void aValueInTheRangeStaysAndOneOutsideGoesToTheNearestEnd() {
        assertThat(PERCENT.clamp(42.5)).isEqualTo(42.5);
        assertThat(PERCENT.clamp(0)).isEqualTo(0);
        assertThat(PERCENT.clamp(100)).isEqualTo(100);
        assertThat(PERCENT.clamp(-1)).isEqualTo(0);
        assertThat(PERCENT.clamp(250)).isEqualTo(100);
    }

    @Test
    void aRangeOfOneValueIsAllowed() {
        assertThat(new NumberRange(3, 3, 0.5, 0).clamp(7)).isEqualTo(3);
    }

    @Test
    void aMaximumBelowTheMinimumAStepOfNothingAndNegativeDecimalsAreRefused() {
        assertThatIllegalArgumentException().isThrownBy(() -> new NumberRange(10, 9.99, 1, 0))
                .withMessage("max must be at least min");
        assertThatIllegalArgumentException().isThrownBy(() -> new NumberRange(0, 10, 0, 0))
                .withMessage("step must be more than 0");
        assertThatIllegalArgumentException().isThrownBy(() -> new NumberRange(0, 10, -1, 0))
                .withMessage("step must be more than 0");
        assertThatIllegalArgumentException().isThrownBy(() -> new NumberRange(0, 10, 1, -1))
                .withMessage("decimals must not be negative");
    }
}
