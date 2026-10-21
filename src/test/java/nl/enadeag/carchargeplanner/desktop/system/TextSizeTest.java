package nl.enadeag.carchargeplanner.desktop.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class TextSizeTest {

    @ParameterizedTest
    @CsvSource({"100, 100", "101, 101", "150, 150", "224, 224", "225, 225"})
    void aValueTheSettingsPageOffersIsTakenAsItIs(int stored, int percent) {
        assertThat(TextSize.clamped(stored)).isEqualTo(new TextSize(percent));
    }

    @ParameterizedTest
    @CsvSource({"99, 100", "0, 100", "-150, 100", "-2147483648, 100", "226, 225", "500, 225", "2147483647, 225"})
    void aValueOutsideTheSettingsPageIsTakenAsTheNearestItOffers(int stored, int percent) {
        assertThat(TextSize.clamped(stored)).isEqualTo(new TextSize(percent));
    }

    @ParameterizedTest
    @ValueSource(ints = {99, 226})
    void aSizeOutsideTheRangeIsRefused(int percent) {
        assertThatThrownBy(() -> new TextSize(percent)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(String.valueOf(percent));
    }

    @ParameterizedTest
    @CsvSource({"100, 14, 14", "125, 14, 17.5", "150, 14, 21", "225, 14, 31.5", "150, 13, 19.5"})
    void aSizeGrowsByTheTextsPercentage(int percent, double normal, double larger) {
        assertThat(new TextSize(percent).of(normal)).isEqualTo(larger);
    }
}
