package nl.enadeag.carchargeplanner.desktop.dashboard.trip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import nl.enadeag.carchargeplanner.desktop.app.Units;
import nl.enadeag.carchargeplanner.desktop.dashboard.trip.TripCheck.Checked;
import nl.enadeag.carchargeplanner.desktop.dashboard.trip.TripCheck.Entered;
import nl.enadeag.carchargeplanner.desktop.dashboard.trip.TripCheck.Field;
import nl.enadeag.carchargeplanner.desktop.text.Language;
import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.desktop.text.TextKey;
import nl.enadeag.carchargeplanner.engine.trip.Trip;
import java.time.Instant;
import java.time.InstantSource;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class TripCheckTest {

    private static final ZoneId BRUSSELS = ZoneId.of("Europe/Brussels");
    private static final LocalDate DAY = LocalDate.of(2026, 7, 3);
    private static final Messages ENGLISH = new Messages(Language.ENGLISH);

    private static Instant at(LocalDate day, int hour, int minute) {
        return day.atTime(hour, minute).atZone(BRUSSELS).toInstant();
    }

    private static TripCheck check(Instant now, Units units, Messages messages) {
        return new TripCheck(BRUSSELS, InstantSource.fixed(now), units, messages);
    }

    /** At 06:00 on the day, in km and English. */
    private static TripCheck morning() {
        return check(at(DAY, 6, 0), Units.METRIC, ENGLISH);
    }

    private static Entered entered(String leaves, String back, String distance) {
        return new Entered(DAY, leaves, DAY, back, distance, false, false);
    }

    @Test
    void aTripOnOneDayIsReadInTheHomesZoneWithItsChoices() {
        var entered = new Entered(DAY, "7:30", DAY, " 18:00 ", "300", true, true);

        assertThat(morning().check(entered))
                .isEqualTo(new Checked.Valid(new Trip(at(DAY, 7, 30), at(DAY, 18, 0), 300, true, true)));
    }

    @Test
    void aTripMayReturnOnALaterDayAtAnEarlierTime() {
        var entered = new Entered(DAY, "19:00", DAY.plusDays(2), "09:15", "1250", false, false);

        assertThat(morning().check(entered))
                .isEqualTo(new Checked.Valid(new Trip(at(DAY, 19, 0), at(DAY.plusDays(2), 9, 15), 1250, false)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "seven", "24:00", "7", "7.30", "07:60"})
    void aDepartureThatIsNotATimeIsRefusedAtItsTime(String typed) {
        assertThat(morning().check(entered(typed, "18:00", "300")))
                .isEqualTo(new Checked.Refused(TextKey.PROBLEM_NOT_A_TIME, Field.DEPARTURE_TIME));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "evening", "25:00"})
    void aReturnThatIsNotATimeIsRefusedAtItsTime(String typed) {
        assertThat(morning().check(entered("7:30", typed, "300")))
                .isEqualTo(new Checked.Refused(TextKey.PROBLEM_NOT_A_TIME, Field.RETURN_TIME));
    }

    @ParameterizedTest
    @CsvSource({"18:00, 7:30", "10:00, 10:00", "10:01, 10:00"})
    void aReturnAtOrBeforeTheDepartureIsRefusedAtTheDayOfReturn(String leaves, String back) {
        assertThat(morning().check(entered(leaves, back, "300")))
                .isEqualTo(new Checked.Refused(TextKey.TRIP_RETURN_BEFORE_DEPARTURE, Field.RETURN_DAY));
    }

    @Test
    void aReturnOnAnEarlierDayIsRefusedAlthoughItsTimeIsLater() {
        var entered = new Entered(DAY.plusDays(1), "7:00", DAY, "18:00", "300", false, false);

        assertThat(morning().check(entered))
                .isEqualTo(new Checked.Refused(TextKey.TRIP_RETURN_BEFORE_DEPARTURE, Field.RETURN_DAY));
    }

    @Test
    void aTripThatHasReturnedIsRefusedButOneUnderWayIsNot() {
        var now = at(DAY, 12, 0);

        assertThat(check(now, Units.METRIC, ENGLISH).check(entered("7:00", "12:00", "40")))
                .isEqualTo(new Checked.Refused(TextKey.TRIP_PASSED, Field.RETURN_DAY));
        assertThat(check(now, Units.METRIC, ENGLISH).check(entered("7:00", "12:01", "40")))
                .isInstanceOf(Checked.Valid.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "far", "12 km", "1,200", "NaN"})
    void aDistanceThatIsNotANumberIsRefusedAtTheDistance(String typed) {
        assertThat(morning().check(entered("7:30", "18:00", typed)))
                .isEqualTo(new Checked.Refused(TextKey.TRIP_DISTANCE_NOT_A_NUMBER, Field.DISTANCE));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-5", "0.0"})
    void aDistanceOfNothingOrLessIsRefusedAtTheDistance(String typed) {
        assertThat(morning().check(entered("7:30", "18:00", typed)))
                .isEqualTo(new Checked.Refused(TextKey.TRIP_DISTANCE_NOT_POSITIVE, Field.DISTANCE));
    }

    @Test
    void theTimesAreCheckedBeforeTheDistance() {
        assertThat(morning().check(entered("18:00", "7:30", "far")))
                .isEqualTo(new Checked.Refused(TextKey.TRIP_RETURN_BEFORE_DEPARTURE, Field.RETURN_DAY));
    }

    @Test
    void aDistanceInMilesIsKeptInKmAndADutchOneIsReadWithItsComma() {
        var miles = check(at(DAY, 6, 0), Units.IMPERIAL, ENGLISH).check(entered("7:30", "18:00", "100"));
        var dutch = check(at(DAY, 6, 0), Units.METRIC, new Messages(Language.DUTCH))
                .check(entered("7:30", "18:00", "12,5"));

        assertThat(((Checked.Valid) miles).trip().distanceKm()).isCloseTo(160.9344, within(1e-9));
        assertThat(((Checked.Valid) dutch).trip().distanceKm()).isEqualTo(12.5);
    }

    @Test
    void aDistanceInMilesTooLargeForKmIsNotANumber() {
        assertThat(check(at(DAY, 6, 0), Units.IMPERIAL, ENGLISH).check(entered("7:30", "18:00", "12" + "0".repeat(307))))
                .isEqualTo(new Checked.Refused(TextKey.TRIP_DISTANCE_NOT_A_NUMBER, Field.DISTANCE));
    }
}
