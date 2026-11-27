package nl.enadeag.carchargeplanner.engine.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import nl.enadeag.carchargeplanner.engine.setting.ConfigSetting;
import nl.enadeag.carchargeplanner.engine.setting.InvalidSetting;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PercentTest {

    @ParameterizedTest
    @ValueSource(doubles = {0, 0.5, 30, 99.99, 100})
    void fromZeroToAHundredIsAPercentage(double value) {
        assertThat(new Percent(value).value()).isEqualTo(value);
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.001, -1, 100.001, 101, Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY})
    void anythingElseIsRefused(double value) {
        assertThatThrownBy(() -> new Percent(value))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("a percentage must be from 0 to 100, not " + value);
    }

    @Test
    void percentagesWithTheSameValueAreEqual() {
        assertThat(new Percent(30)).isEqualTo(new Percent(30.0)).isNotEqualTo(new Percent(31));
    }

    @ParameterizedTest
    @ValueSource(doubles = {0, 22.5, 100})
    void aSettingsPercentageFromZeroToAHundredIsMade(double value) {
        assertThat(Percent.of(ConfigSetting.CAR_TRIP_SURCHARGE, value)).isEqualTo(new Percent(value));
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.001, 100.001, Double.NaN})
    void aSettingsPercentageOutsideIsRefusedAsThatSettingsValue(double value) {
        assertThatThrownBy(() -> Percent.of(ConfigSetting.CAR_TRIP_SURCHARGE, value))
                .isInstanceOfSatisfying(InvalidSetting.class, refused -> {
                    assertThat(refused.setting()).isEqualTo(ConfigSetting.CAR_TRIP_SURCHARGE);
                    assertThat(refused.rule()).isEqualTo(InvalidSetting.Rule.OUT_OF_RANGE);
                })
                .hasMessage("a percentage must be from 0 to 100, not " + value);
    }
}
