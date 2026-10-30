package nl.enadeag.carchargeplanner.engine.notify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import nl.enadeag.carchargeplanner.engine.trip.Trip;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TripShortTest {

    private static final ZoneId BRUSSELS = ZoneId.of("Europe/Brussels");
    private static final Trip TRIP = new Trip(Instant.parse("2026-07-05T06:30:00Z"),
            Instant.parse("2026-07-05T16:00:00Z"), 420, false);

    @ParameterizedTest
    @ValueSource(doubles = {0, -1, Double.NaN, Double.POSITIVE_INFINITY})
    void aTripThatIsNotShortByAFiniteDistanceIsRefused(double kmShort) {
        assertThatThrownBy(() -> new TripShort(TRIP, kmShort, BRUSSELS)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aTripShortByAnyDistanceAboveNothingIsKept() {
        assertThat(new TripShort(TRIP, 0.01, BRUSSELS).kmShort()).isEqualTo(0.01);
    }
}
