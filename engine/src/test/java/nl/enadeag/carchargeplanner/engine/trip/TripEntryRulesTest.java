package nl.enadeag.carchargeplanner.engine.trip;

import static org.assertj.core.api.Assertions.assertThat;

import nl.enadeag.carchargeplanner.engine.trip.TripEntryRules.Checked;
import nl.enadeag.carchargeplanner.engine.trip.TripEntryRules.Entered;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TripEntryRulesTest {

    private static final ZoneId BRUSSELS = ZoneId.of("Europe/Brussels");
    private static final LocalDate DAY = LocalDate.of(2026, 7, 3);
    private static final Instant MORNING = at(DAY.atTime(6, 0));

    private static Instant at(LocalDateTime time) {
        return time.atZone(BRUSSELS).toInstant();
    }

    private static Entered entered(LocalDateTime leaves, LocalDateTime back, double km) {
        return new Entered(leaves, back, km, false, false);
    }

    private static Checked check(LocalDateTime leaves, LocalDateTime back, double km) {
        return TripEntryRules.check(entered(leaves, back, km), MORNING, BRUSSELS);
    }

    @Test
    void aTripIsReadInTheHomesZoneWithItsChoices() {
        var entered = new Entered(DAY.atTime(7, 30), DAY.atTime(18, 0), 300.4, true, true);

        assertThat(TripEntryRules.check(entered, MORNING, BRUSSELS)).isEqualTo(new Checked.Valid(
                new Trip(at(DAY.atTime(7, 30)), at(DAY.atTime(18, 0)), 300.4, true, true)));
    }

    @Test
    void aDepartureBefore1970IsItsOwnProblemAndOneAtItsFirstMomentIsAccepted() {
        var utc = ZoneOffset.UTC;
        var epoch = LocalDateTime.of(1970, 1, 1, 0, 0);
        var now = Instant.EPOCH;

        assertThat(TripEntryRules.check(entered(epoch.minusMinutes(1), epoch.plusHours(1), 10), now, utc))
                .isEqualTo(new Checked.Refused(TripProblem.DEPARTURE_BEFORE_EARLIEST));
        assertThat(TripEntryRules.check(entered(epoch, epoch.plusHours(1), 10), now, utc))
                .isInstanceOf(Checked.Valid.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"18:00", "17:59"})
    void aReturnAtOrBeforeTheDepartureIsRefused(String back) {
        assertThat(check(DAY.atTime(18, 0), DAY.atTime(LocalTime.parse(back)), 10))
                .isEqualTo(new Checked.Refused(TripProblem.RETURN_NOT_AFTER_DEPARTURE));
    }

    @Test
    void aDepartureTheClockSkipsCountsAsTheTimeAfterTheGapSoAReturnThenIsNotAfterIt() {
        // in Brussels the clock goes from 02:00 to 03:00 on 29 March 2026
        var spring = LocalDate.of(2026, 3, 29);
        var now = at(spring.minusDays(1).atStartOfDay());

        assertThat(TripEntryRules.check(entered(spring.atTime(2, 30), spring.atTime(3, 15), 10), now, BRUSSELS))
                .isEqualTo(new Checked.Refused(TripProblem.RETURN_NOT_AFTER_DEPARTURE));
    }

    @Test
    void aTimeTheClockRepeatsCountsAsItsFirstOccurrence() {
        // in Amsterdam 02:00 to 03:00 happens twice on 25 October 2026, first in summer time
        var amsterdam = ZoneId.of("Europe/Amsterdam");
        var autumn = LocalDate.of(2026, 10, 25);
        var now = autumn.minusDays(1).atStartOfDay(amsterdam).toInstant();

        assertThat(TripEntryRules.check(entered(autumn.atTime(2, 30), autumn.atTime(2, 45), 10), now, amsterdam))
                .isEqualTo(new Checked.Valid(new Trip(Instant.parse("2026-10-25T00:30:00Z"),
                        Instant.parse("2026-10-25T00:45:00Z"), 10, false, false)));
    }

    @Test
    void aReturnAtNowIsPassedAndOneAMinuteLaterIsNot() {
        var now = at(DAY.atTime(18, 0));

        assertThat(TripEntryRules.check(entered(DAY.atTime(8, 0), DAY.atTime(18, 0), 10), now, BRUSSELS))
                .isEqualTo(new Checked.Refused(TripProblem.PASSED));
        assertThat(TripEntryRules.check(entered(DAY.atTime(8, 0), DAY.atTime(18, 1), 10), now, BRUSSELS))
                .isInstanceOf(Checked.Valid.class);
    }

    @Test
    void aTripOf366DaysIsAcceptedAndAMinuteLongerIsTooLong() {
        var leaves = DAY.atTime(8, 0);

        assertThat(check(leaves, leaves.plusDays(366), 10)).isInstanceOf(Checked.Valid.class);
        assertThat(check(leaves, leaves.plusDays(366).plusMinutes(1), 10))
                .isEqualTo(new Checked.Refused(TripProblem.TOO_LONG));
    }

    @ParameterizedTest
    @ValueSource(doubles = {0, -0.0, -1, Double.NEGATIVE_INFINITY})
    void aDistanceOfNothingOrLessIsNotPositive(double km) {
        assertThat(check(DAY.atTime(8, 0), DAY.atTime(18, 0), km))
                .isEqualTo(new Checked.Refused(TripProblem.DISTANCE_NOT_POSITIVE));
    }

    @ParameterizedTest
    @ValueSource(doubles = {Double.NaN, Double.POSITIVE_INFINITY})
    void aDistanceThatIsNoNumberOrTooLargeToBeOneIsNotANumber(double km) {
        assertThat(check(DAY.atTime(8, 0), DAY.atTime(18, 0), km))
                .isEqualTo(new Checked.Refused(TripProblem.DISTANCE_NOT_A_NUMBER));
    }

    @Test
    void aTripOfTheLongestDistanceIsAcceptedAndALongerOneIsTooFar() {
        assertThat(check(DAY.atTime(8, 0), DAY.atTime(18, 0), Trip.MAX_DISTANCE_KM)).isInstanceOf(Checked.Valid.class);
        assertThat(check(DAY.atTime(8, 0), DAY.atTime(18, 0), Math.nextUp((double) Trip.MAX_DISTANCE_KM)))
                .isEqualTo(new Checked.Refused(TripProblem.DISTANCE_TOO_FAR));
    }

    @Test
    void aReturnInTheYear10000IsTooLongButOnlyAfterTheDistancesProblems() {
        var utc = ZoneOffset.UTC;
        var leaves = LocalDateTime.of(9999, 12, 31, 12, 0);
        var back = LocalDateTime.of(10_000, 1, 1, 0, 0);
        var now = Instant.parse("2026-07-03T00:00:00Z");

        assertThat(TripEntryRules.check(entered(leaves, back, 10), now, utc))
                .isEqualTo(new Checked.Refused(TripProblem.TOO_LONG));
        assertThat(TripEntryRules.check(entered(leaves, back, Double.NaN), now, utc))
                .isEqualTo(new Checked.Refused(TripProblem.DISTANCE_NOT_A_NUMBER));
        assertThat(TripEntryRules.check(entered(leaves, back.minusNanos(1), 10), now, utc))
                .isInstanceOf(Checked.Valid.class);
    }

    @Test
    void theTimesAreCheckedBeforeTheDistance() {
        var now = at(DAY.atTime(20, 0));

        assertThat(TripEntryRules.check(entered(DAY.atTime(8, 0), DAY.atTime(18, 0), Double.NaN), now, BRUSSELS))
                .isEqualTo(new Checked.Refused(TripProblem.PASSED));
        assertThat(check(DAY.atTime(18, 0), DAY.atTime(8, 0), -1))
                .isEqualTo(new Checked.Refused(TripProblem.RETURN_NOT_AFTER_DEPARTURE));
    }

    @Test
    void aNewTripsEntryOpensFromEightToSixAndOffersThreeWeeks() {
        assertThat(TripEntryRules.FIRST_DEPARTURE).isEqualTo(LocalTime.of(8, 0));
        assertThat(TripEntryRules.FIRST_RETURN).isEqualTo(LocalTime.of(18, 0));
        assertThat(TripEntryRules.DAYS_OFFERED).isEqualTo(21);
    }
}
