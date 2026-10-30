package nl.enadeag.carchargeplanner.engine.trip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import nl.enadeag.carchargeplanner.engine.store.StoredJson;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TripFileTest {

    private static final ZoneId TZ = ZoneId.of("Europe/Amsterdam");
    private static final LocalDate DAY = LocalDate.of(2026, 7, 3);
    private static final Instant NOW = DAY.atTime(7, 0).atZone(TZ).toInstant();

    @TempDir
    Path folder;

    private TripFile file() {
        return new TripFile(folder.resolve("data").resolve("trips.json"));
    }

    /** A trip on a day after the test's day, from 08:00 to 18:00. */
    private static Trip trip(int daysLater, double distanceKm) {
        var date = DAY.plusDays(daysLater);
        return new Trip(date.atTime(8, 0).atZone(TZ).toInstant(), date.atTime(18, 0).atZone(TZ).toInstant(),
                distanceKm, false);
    }

    @Test
    void tripsAreKeptInOrderOfTheirDepartureAndEachOnce() throws IOException {
        var later = trip(2, 120);
        var sooner = trip(1, 300);
        file().add(later, NOW);
        file().add(sooner, NOW);

        assertThat(file().add(sooner, NOW)).containsExactly(sooner, later);
        assertThat(file().trips(NOW)).containsExactly(sooner, later);
    }

    @Test
    void everyPartOfATripIsKept() throws IOException {
        var trip = new Trip(Instant.parse("2026-07-04T06:15:00Z"), Instant.parse("2026-07-05T21:45:30Z"), 412.5, true);
        var withoutTheCar = new Trip(Instant.parse("2026-07-06T06:15:00Z"), Instant.parse("2026-07-06T21:45:30Z"), 80,
                false, true);
        file().add(trip, NOW);
        file().add(withoutTheCar, NOW);

        assertThat(file().trips(NOW)).containsExactly(trip, withoutTheCar);
    }

    @Test
    void onlyATripWithoutThisCarSaysSoInTheFile() throws IOException {
        var path = folder.resolve("trips.json");
        var withTheCar = trip(1, 300);
        var withoutTheCar = new Trip(withTheCar.departure().plusSeconds(86_400),
                withTheCar.returnTime().plusSeconds(86_400), 80, false, true);
        new TripFile(path).add(withTheCar, NOW);
        new TripFile(path).add(withoutTheCar, NOW);

        var rows = StoredJson.parse(Files.readString(path)).path("trips");
        assertThat(rows.get(0).has("notWithThisCar")).isFalse();
        assertThat(rows.get(1).path("notWithThisCar").isBoolean()).isTrue();
        assertThat(rows.get(1).path("notWithThisCar").booleanValue()).isTrue();
    }

    @Test
    void theFileHoldsTheTripsInTheShapeItsDocumentationGives() throws IOException {
        var path = Files.writeString(folder.resolve("trips.json"), """
                {"version": 1, "trips": [{"departure": "2026-07-04T06:00:00Z", "return": "2026-07-04T18:00:00Z",
                  "distanceKm": 300, "canChargeAtDestination": false}]}
                """);
        var stored = new Trip(Instant.parse("2026-07-04T06:00:00Z"), Instant.parse("2026-07-04T18:00:00Z"), 300, false);

        assertThat(new TripFile(path).trips(NOW)).containsExactly(stored);
    }

    @Test
    void changingATripReplacesIt() throws IOException {
        var trip = trip(1, 300);
        var other = trip(2, 120);
        file().add(trip, NOW);
        file().add(other, NOW);
        var changed = new Trip(trip.departure(), trip.returnTime(), 350, true);

        assertThat(file().change(trip, changed, NOW)).containsExactly(changed, other);
        assertThat(file().trips(NOW)).containsExactly(changed, other);
    }

    @Test
    void changingATripThatIsNoLongerStoredAddsTheChangedOne() throws IOException {
        var other = trip(2, 120);
        file().add(other, NOW);
        var changed = trip(1, 350);

        assertThat(file().change(trip(1, 300), changed, NOW)).containsExactly(changed, other);
    }

    @Test
    void changingATripIntoOneThatIsStoredKeepsItOnce() throws IOException {
        var trip = trip(1, 300);
        var other = trip(2, 120);
        file().add(trip, NOW);
        file().add(other, NOW);

        assertThat(file().change(trip, other, NOW)).containsExactly(other);
    }

    @Test
    void removingGivesTheTripsThatAreLeftAndAnUnknownOneChangesNothing() throws IOException {
        var first = trip(1, 300);
        var second = trip(2, 120);
        file().add(first, NOW);
        file().add(second, NOW);

        assertThat(file().remove(first, NOW)).containsExactly(second);
        assertThat(file().remove(first, NOW)).containsExactly(second);
        assertThat(file().trips(NOW)).containsExactly(second);
    }

    @ParameterizedTest
    @ValueSource(longs = {-1, 0})
    void aTripIsReadUntilItReturnedADayAgo(long secondsLater) throws IOException {
        var trip = trip(0, 300);
        file().add(trip, NOW);
        var now = trip.returnTime().plus(TripFile.KEEP_AFTER_RETURN).plusSeconds(secondsLater);

        assertThat(file().trips(now)).isEqualTo(secondsLater < 0 ? List.of(trip) : List.of());
    }

    @Test
    void aPastTripIsDroppedFromTheFileAtTheNextChange() throws IOException {
        var past = trip(0, 300);
        var next = trip(1, 120);
        var later = trip(2, 50);
        file().add(past, NOW);
        file().add(next, NOW);
        var now = past.returnTime().plus(TripFile.KEEP_AFTER_RETURN);

        assertThat(file().add(later, now)).containsExactly(next, later);
        assertThat(file().remove(later, now)).containsExactly(next);
        assertThat(file().change(next, later, now)).containsExactly(later);
        assertThat(file().trips(NOW)).containsExactly(later);
    }

    @Test
    void aTripThatIsPastWhenItIsAddedIsNotKept() throws IOException {
        var trip = trip(-2, 300);

        assertThat(file().add(trip, NOW)).isEmpty();
        assertThat(file().trips(trip.departure())).isEmpty();
    }

    @Test
    void aMissingOrDamagedFileHoldsNoTrips() throws IOException {
        assertThat(file().trips(NOW)).isEmpty();
        var damaged = Files.writeString(folder.resolve("damaged.json"), "{oops");
        assertThat(new TripFile(damaged).trips(NOW)).isEmpty();
    }

    @Test
    void aDamagedEntryIsLeftOutAndTheRestKept() throws IOException {
        var path = Files.writeString(folder.resolve("trips.json"), """
                {"trips": [{"departure": 1}, "junk",
                  {"departure": "2026-07-04T08:00:00Z", "return": "2026-07-04T06:00:00Z", "distanceKm": 30,
                   "canChargeAtDestination": false},
                  {"departure": "2026-07-04T06:00:00Z", "return": "2026-07-04T08:00:00Z", "distanceKm": 0,
                   "canChargeAtDestination": false},
                  {"departure": "2026-07-04T06:00:00Z", "return": "2026-07-04T08:00:00Z", "distanceKm": "30",
                   "canChargeAtDestination": false},
                  {"departure": "2026-07-04T06:00:00Z", "return": "2026-07-04T08:00:00Z", "distanceKm": 30,
                   "canChargeAtDestination": "no"},
                  {"departure": "2026-07-04T06:00:00Z", "return": "2026-07-04T08:00:00Z", "distanceKm": 30},
                  {"departure": "2026-07-04T06:00:00Z", "return": "2026-07-04T08:00:00Z", "distanceKm": 30,
                   "canChargeAtDestination": false, "notWithThisCar": "yes"},
                  {"departure": "2026-07-04T06:00:00Z", "return": "4 July", "distanceKm": 30,
                   "canChargeAtDestination": false},
                  {"departure": "2026-07-04T06:00:00Z", "distanceKm": 30, "canChargeAtDestination": false},
                  {"departure": "2026-07-04T06:00:00Z", "return": "2026-07-04T08:00:00Z", "distanceKm": 30,
                   "canChargeAtDestination": true}]}
                """);

        assertThat(new TripFile(path).trips(NOW)).containsExactly(
                new Trip(Instant.parse("2026-07-04T06:00:00Z"), Instant.parse("2026-07-04T08:00:00Z"), 30, true));
    }

    @Test
    void aTripThatCannotBeSavedIsReported() throws IOException {
        Files.writeString(folder.resolve("data"), "a file where the folder should be");

        assertThatThrownBy(() -> file().add(trip(1, 300), NOW)).isInstanceOf(IOException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{oops", "{\"version\": 2, \"trips\": []}", "{\"trips\": {}}",
        "{\"trips\": [{\"departure\": 1}, {\"departure\": \"2026-07-04T06:00:00Z\", \"return\": \"2026-07-04T08:00:00Z\","
            + " \"distanceKm\": 30, \"canChargeAtDestination\": false}]}"})
    void aFileThatCannotBeReadWholeIsNeverOverwritten(String content) throws IOException {
        var path = Files.writeString(folder.resolve("trips.json"), content);
        var trip = trip(1, 300);

        assertThatThrownBy(() -> new TripFile(path).add(trip, NOW)).isInstanceOf(IOException.class)
                .hasMessageContaining("left as it is");
        assertThatThrownBy(() -> new TripFile(path).change(trip, trip, NOW)).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> new TripFile(path).remove(trip, NOW)).isInstanceOf(IOException.class);
        assertThat(path).hasContent(content);
    }

    @Test
    void aFileWithoutAVersionIsReadAndAWrittenOneHasVersionOne() throws IOException {
        var path = Files.writeString(folder.resolve("trips.json"), """
                {"trips": [{"departure": "2026-07-04T06:00:00Z", "return": "2026-07-04T08:00:00Z", "distanceKm": 30,
                  "canChargeAtDestination": false}]}
                """);
        var stored = new Trip(Instant.parse("2026-07-04T06:00:00Z"), Instant.parse("2026-07-04T08:00:00Z"), 30, false);
        assertThat(new TripFile(path).trips(NOW)).containsExactly(stored);

        var added = trip(2, 120);
        new TripFile(path).add(added, NOW);

        assertThat(StoredJson.parse(Files.readString(path)).path("version").intValue()).isEqualTo(1);
        assertThat(new TripFile(path).trips(NOW)).containsExactly(stored, added);
    }

    @Test
    void tripsWrittenOutOfOrderAreReadInOrderOfTheirDeparture() throws IOException {
        var path = Files.writeString(folder.resolve("trips.json"), """
                {"trips": [{"departure": "2026-07-05T06:00:00Z", "return": "2026-07-05T08:00:00Z", "distanceKm": 30,
                  "canChargeAtDestination": false},
                  {"departure": "2026-07-04T06:00:00Z", "return": "2026-07-04T08:00:00Z", "distanceKm": 40,
                  "canChargeAtDestination": false}]}
                """);

        assertThat(new TripFile(path).trips(NOW)).extracting(Trip::distanceKm).containsExactly(40.0, 30.0);
    }
}
