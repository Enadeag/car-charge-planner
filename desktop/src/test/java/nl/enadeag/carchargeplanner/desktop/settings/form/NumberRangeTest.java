package nl.enadeag.carchargeplanner.desktop.settings.form;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class NumberRangeTest {

    @Test
    void theArrowsStopAtTheLimitAsTheFieldShowsIt() {
        var percent = NumberRange.of(Limit.fromTo(0, 100), 5, 0);
        assertThat(percent.lowest()).isEqualTo(0);
        assertThat(percent.highest()).isEqualTo(100);
        var open = NumberRange.of(Limit.atLeast(0), 0.05, 2);
        assertThat(open.highest()).as("no highest end, so no stop").isEqualTo(Double.POSITIVE_INFINITY);
    }

    @Test
    void aWholeNumberFieldShowsNoDecimals() {
        assertThat(NumberRange.whole(Limit.fromTo(1, 80), 1)).isEqualTo(new NumberRange(Limit.fromTo(1, 80), 1, 0,
                true));
        assertThat(NumberRange.of(Limit.fromTo(1, 80), 1, 0).whole()).isFalse();
    }

    @Test
    void aStepOfNothingNegativeDecimalsAndAWholeNumberWithDecimalsAreRefused() {
        var limit = Limit.fromTo(0, 10);
        assertThatIllegalArgumentException().isThrownBy(() -> NumberRange.of(limit, 0, 0))
                .withMessage("step must be more than 0");
        assertThatIllegalArgumentException().isThrownBy(() -> NumberRange.of(limit, -1, 0))
                .withMessage("step must be more than 0");
        assertThatIllegalArgumentException().isThrownBy(() -> NumberRange.of(limit, 1, -1))
                .withMessage("decimals must not be negative");
        assertThatIllegalArgumentException().isThrownBy(() -> new NumberRange(limit, 1, 1, true))
                .withMessage("a whole-number field shows no decimals");
    }
}
