package nl.enadeag.carchargeplanner.engine.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class NotifyLeadChoicesTest {

    private static Duration minutes(long minutes) {
        return Duration.ofMinutes(minutes);
    }

    @Test
    void theStandardLeadsAreTheStartAndFiveToSixtyMinutesAhead() {
        assertThat(NotifyLeadChoices.STANDARD).containsExactly(Duration.ZERO, minutes(5), minutes(10), minutes(15),
                minutes(30), minutes(60));
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 5, 10, 15, 30, 60})
    void aStandardLeadChosenOffersTheStandardLeadsAlone(long chosen) {
        assertThat(NotifyLeadChoices.offered(minutes(chosen))).isEqualTo(NotifyLeadChoices.STANDARD);
    }

    @Test
    void anotherLeadIsOfferedAsItIsInItsPlaceByLength() {
        assertThat(NotifyLeadChoices.offered(minutes(20))).containsExactly(Duration.ZERO, minutes(5), minutes(10),
                minutes(15), minutes(20), minutes(30), minutes(60));
    }

    @Test
    void aLeadLongerThanAnyStandardOneComesLast() {
        assertThat(NotifyLeadChoices.offered(minutes(90))).endsWith(minutes(60), minutes(90)).hasSize(7);
    }

    @Test
    void aNegativeLeadIsRefused() {
        assertThatThrownBy(() -> NotifyLeadChoices.offered(minutes(-5))).isInstanceOf(IllegalArgumentException.class);
    }
}
