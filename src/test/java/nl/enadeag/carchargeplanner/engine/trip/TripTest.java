package nl.enadeag.carchargeplanner.engine.trip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TripTest {

    private static final Instant DEPARTURE = Instant.parse("2026-07-04T06:00:00Z");
    private static final Instant RETURN = Instant.parse("2026-07-04T18:00:00Z");

    @ParameterizedTest
    @ValueSource(strings = {"2026-07-04T06:00:00Z", "2026-07-04T05:59:59Z"})
    void aTripMustReturnAfterItsDeparture(String back) {
        assertThatThrownBy(() -> new Trip(DEPARTURE, Instant.parse(back), 300, false))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("return after its departure");
    }

    @ParameterizedTest
    @ValueSource(doubles = {0, -0.0, -1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void aTripMustHaveAFiniteDistanceOfMoreThanNothing(double distanceKm) {
        assertThatThrownBy(() -> new Trip(DEPARTURE, RETURN, distanceKm, false))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("distance");
    }

    @Test
    void theShortestTripTakesAMomentAndTheSmallestDistance() {
        var trip = new Trip(DEPARTURE, DEPARTURE.plusNanos(1), Double.MIN_VALUE, true);
        assertThat(trip.distanceKm()).isEqualTo(Double.MIN_VALUE);
    }
}
