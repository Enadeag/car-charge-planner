package nl.enadeag.carchargeplanner.engine.trip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
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

    private static final ZoneId BRUSSELS = ZoneId.of("Europe/Brussels");
    /** Its clocks went from 00:00 to 01:00 on 6 September 2026: that day had no midnight. */
    private static final ZoneId SANTIAGO = ZoneId.of("America/Santiago");

    private static Trip trip(String departure, String back, ZoneId zone) {
        return new Trip(LocalDateTime.parse(departure).atZone(zone).toInstant(),
                LocalDateTime.parse(back).atZone(zone).toInstant(), 300, false);
    }

    @Test
    void aTripTakesTheDaysFromItsDepartureToItsReturn() {
        var trip = trip("2026-07-04T08:00", "2026-07-06T18:00", BRUSSELS);
        assertThat(trip.firstDay(BRUSSELS)).isEqualTo(LocalDate.of(2026, 7, 4));
        assertThat(trip.lastDay(BRUSSELS)).isEqualTo(LocalDate.of(2026, 7, 6));
        assertThat(trip.covers(LocalDate.of(2026, 7, 3), BRUSSELS)).isFalse();
        assertThat(trip.covers(LocalDate.of(2026, 7, 4), BRUSSELS)).isTrue();
        assertThat(trip.covers(LocalDate.of(2026, 7, 5), BRUSSELS)).isTrue();
        assertThat(trip.covers(LocalDate.of(2026, 7, 6), BRUSSELS)).isTrue();
        assertThat(trip.covers(LocalDate.of(2026, 7, 7), BRUSSELS)).isFalse();
    }

    @Test
    void aReturnAtMidnightTakesNoTimeOfTheDayItReturnsOn() {
        var trip = trip("2026-07-04T08:00", "2026-07-05T00:00", BRUSSELS);
        assertThat(trip.lastDay(BRUSSELS)).isEqualTo(LocalDate.of(2026, 7, 4));
        assertThat(trip.covers(LocalDate.of(2026, 7, 5), BRUSSELS)).isFalse();
    }

    @Test
    void aReturnAMinuteAfterMidnightTakesTimeOfTheDayItReturnsOn() {
        var trip = trip("2026-07-04T08:00", "2026-07-05T00:01", BRUSSELS);
        assertThat(trip.lastDay(BRUSSELS)).isEqualTo(LocalDate.of(2026, 7, 5));
        assertThat(trip.covers(LocalDate.of(2026, 7, 5), BRUSSELS)).isTrue();
    }

    @Test
    void aReturnAtTheFirstMomentOfADayWithoutMidnightTakesNoTimeOfThatDay() {
        var dayWithoutMidnight = LocalDate.of(2026, 9, 6);
        var firstMoment = dayWithoutMidnight.atStartOfDay(SANTIAGO);
        assertThat(firstMoment.toLocalTime()).isEqualTo(LocalTime.of(1, 0));
        var trip = new Trip(firstMoment.minusHours(10).toInstant(), firstMoment.toInstant(), 300, false);
        assertThat(trip.lastDay(SANTIAGO)).isEqualTo(dayWithoutMidnight.minusDays(1));
        assertThat(trip.covers(dayWithoutMidnight, SANTIAGO)).isFalse();
    }

    @Test
    void aTripLeavingAndReturningTheSameDayTakesOnlyThatDay() {
        var trip = trip("2026-07-04T08:00", "2026-07-04T18:00", BRUSSELS);
        assertThat(trip.firstDay(BRUSSELS)).isEqualTo(trip.lastDay(BRUSSELS));
    }

    @Test
    void theZoneDecidesTheDays() {
        var trip = trip("2026-07-05T01:00", "2026-07-06T01:00", BRUSSELS);
        var utc = ZoneId.of("UTC");
        assertThat(trip.firstDay(BRUSSELS)).isEqualTo(LocalDate.of(2026, 7, 5));
        assertThat(trip.lastDay(BRUSSELS)).isEqualTo(LocalDate.of(2026, 7, 6));
        assertThat(trip.firstDay(utc)).isEqualTo(LocalDate.of(2026, 7, 4));
        assertThat(trip.lastDay(utc)).isEqualTo(LocalDate.of(2026, 7, 5));
    }
}
