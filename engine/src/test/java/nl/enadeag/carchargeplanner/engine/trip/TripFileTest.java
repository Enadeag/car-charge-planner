package nl.enadeag.carchargeplanner.engine.trip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import nl.enadeag.carchargeplanner.engine.store.FilePrivacy;
import nl.enadeag.carchargeplanner.engine.store.PrivateFiles;
import nl.enadeag.carchargeplanner.engine.store.StoredJson;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TripFileTest {

    private static final PrivateFiles FILES = new PrivateFiles(FilePrivacy.NONE);

    private static final ZoneId TZ = ZoneId.of("Europe/Amsterdam");
    private static final LocalDate DAY = LocalDate.of(2026, 7, 3);
    private static final Instant NOW = DAY.atTime(7, 0).atZone(TZ).toInstant();
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    /** Enough changes from each of two threads that, without one change at a time, some overlap. */
    private static final int TRIPS_PER_THREAD = 40;

    @TempDir
    Path folder;

    private TripFile file() {
        return new TripFile(folder.resolve("data").resolve("trips.json"), FILES, CLOCK);
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
    void tripsAddedFromTwoThreadsAtOnceAreAllKept() throws Exception {
        var shared = file();
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var firsts = pool.submit(adding(shared, start, 1));
            var seconds = pool.submit(adding(shared, start, 2));
            start.countDown();
            firsts.get(30, TimeUnit.SECONDS);
            seconds.get(30, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
        assertThat(file().trips(NOW)).hasSize(2 * TRIPS_PER_THREAD);
    }

    /** Adds trips of one distance, one per day, once the start is given. */
    private static Callable<Void> adding(TripFile shared, CountDownLatch start, double distanceKm) {
        return () -> {
            start.await();
            for (int day = 1; day <= TRIPS_PER_THREAD; day++) {
                shared.add(trip(day, distanceKm), NOW);
            }
            return null;
        };
    }

    @Test
    void tripsChangedFromTwoThreadsAtOnceAreAllChanged() throws Exception {
        var shared = file();
        var firsts = trips(1);
        var seconds = trips(2);
        for (var trip : firsts) {
            shared.add(trip, NOW);
        }
        for (var trip : seconds) {
            shared.add(trip, NOW);
        }

        atOnce(changing(shared, firsts, 11), changing(shared, seconds, 12));

        var distances = file().trips(NOW).stream().map(Trip::distanceKm).toList();
        assertThat(distances).hasSize(2 * TRIPS_PER_THREAD);
        assertThat(distances).filteredOn(km -> km == 11).hasSize(TRIPS_PER_THREAD);
        assertThat(distances).filteredOn(km -> km == 12).hasSize(TRIPS_PER_THREAD);
    }

    @Test
    void tripsRemovedFromTwoThreadsAtOnceAreAllGone() throws Exception {
        var shared = file();
        var firsts = trips(1);
        var seconds = trips(2);
        for (var trip : firsts) {
            shared.add(trip, NOW);
        }
        for (var trip : seconds) {
            shared.add(trip, NOW);
        }

        atOnce(removing(shared, firsts), removing(shared, seconds));

        assertThat(file().trips(NOW)).isEmpty();
    }

    /** One trip of the distance on each of the days after the test's day. */
    private static List<Trip> trips(double distanceKm) {
        var trips = new ArrayList<Trip>();
        for (int day = 1; day <= TRIPS_PER_THREAD; day++) {
            trips.add(trip(day, distanceKm));
        }
        return trips;
    }

    /** Changes each trip's distance to the one given, one by one. */
    private static Callable<Void> changing(TripFile shared, List<Trip> trips, double distanceKm) {
        return () -> {
            for (var trip : trips) {
                shared.change(trip, new Trip(trip.departure(), trip.returnTime(), distanceKm, false), NOW);
            }
            return null;
        };
    }

    /** Removes the trips one by one. */
    private static Callable<Void> removing(TripFile shared, List<Trip> trips) {
        return () -> {
            for (var trip : trips) {
                shared.remove(trip, NOW);
            }
            return null;
        };
    }

    /** Runs both from a thread of its own, starting them at once, and waits until both are done. */
    private static void atOnce(Callable<Void> one, Callable<Void> other) throws Exception {
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> {
                start.await();
                return one.call();
            });
            var second = pool.submit(() -> {
                start.await();
                return other.call();
            });
            start.countDown();
            first.get(30, TimeUnit.SECONDS);
            second.get(30, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
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
        new TripFile(path, FILES, CLOCK).add(withTheCar, NOW);
        new TripFile(path, FILES, CLOCK).add(withoutTheCar, NOW);

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

        assertThat(new TripFile(path, FILES, CLOCK).trips(NOW)).containsExactly(stored);
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
        assertThat(new TripFile(damaged, FILES, CLOCK).trips(NOW)).isEmpty();
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

        assertThat(new TripFile(path, FILES, CLOCK).trips(NOW)).containsExactly(
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

        assertThatThrownBy(() -> new TripFile(path, FILES, CLOCK).add(trip, NOW)).isInstanceOf(IOException.class)
                .hasMessageContaining("left as it is");
        assertThatThrownBy(() -> new TripFile(path, FILES, CLOCK).change(trip, trip, NOW))
                .isInstanceOf(IOException.class);
        assertThatThrownBy(() -> new TripFile(path, FILES, CLOCK).remove(trip, NOW)).isInstanceOf(IOException.class);
        assertThat(path).hasContent(content);
    }

    @Test
    void aFileWithoutAVersionIsReadAndAWrittenOneHasVersionOne() throws IOException {
        var path = Files.writeString(folder.resolve("trips.json"), """
                {"trips": [{"departure": "2026-07-04T06:00:00Z", "return": "2026-07-04T08:00:00Z", "distanceKm": 30,
                  "canChargeAtDestination": false}]}
                """);
        var stored = new Trip(Instant.parse("2026-07-04T06:00:00Z"), Instant.parse("2026-07-04T08:00:00Z"), 30, false);
        assertThat(new TripFile(path, FILES, CLOCK).trips(NOW)).containsExactly(stored);

        var added = trip(2, 120);
        new TripFile(path, FILES, CLOCK).add(added, NOW);

        assertThat(StoredJson.parse(Files.readString(path)).path("version").intValue()).isEqualTo(1);
        assertThat(new TripFile(path, FILES, CLOCK).trips(NOW)).containsExactly(stored, added);
    }

    @Test
    void tripsWrittenOutOfOrderAreReadInOrderOfTheirDeparture() throws IOException {
        var path = Files.writeString(folder.resolve("trips.json"), """
                {"trips": [{"departure": "2026-07-05T06:00:00Z", "return": "2026-07-05T08:00:00Z", "distanceKm": 30,
                  "canChargeAtDestination": false},
                  {"departure": "2026-07-04T06:00:00Z", "return": "2026-07-04T08:00:00Z", "distanceKm": 40,
                  "canChargeAtDestination": false}]}
                """);

        assertThat(new TripFile(path, FILES, CLOCK).trips(NOW)).extracting(Trip::distanceKm)
                .containsExactly(40.0, 30.0);
    }
}
