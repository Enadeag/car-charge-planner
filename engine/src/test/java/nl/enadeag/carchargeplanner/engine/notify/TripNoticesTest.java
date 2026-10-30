package nl.enadeag.carchargeplanner.engine.notify;

import static org.assertj.core.api.Assertions.assertThat;

import nl.enadeag.carchargeplanner.LogCapture;
import nl.enadeag.carchargeplanner.engine.config.ChargingHours;
import nl.enadeag.carchargeplanner.engine.config.ConfigFile;
import nl.enadeag.carchargeplanner.engine.config.ConfigProblem;
import nl.enadeag.carchargeplanner.engine.config.EngineConfig;
import nl.enadeag.carchargeplanner.engine.config.Percent;
import nl.enadeag.carchargeplanner.engine.config.Phases;
import nl.enadeag.carchargeplanner.engine.config.PlanningPreferences;
import nl.enadeag.carchargeplanner.engine.config.ProviderId;
import nl.enadeag.carchargeplanner.engine.forecast.ForecastSource;
import nl.enadeag.carchargeplanner.engine.forecast.Freshness;
import nl.enadeag.carchargeplanner.engine.forecast.ProviderRole;
import nl.enadeag.carchargeplanner.engine.plan.TripAdvice;
import nl.enadeag.carchargeplanner.engine.plan.TripGridCharge;
import nl.enadeag.carchargeplanner.engine.plan.Window;
import nl.enadeag.carchargeplanner.engine.trip.Trip;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** What the owner is told about a trip, and when not. */
class TripNoticesTest {

    private static final ZoneId BRUSSELS = ZoneId.of("Europe/Brussels");
    private static final LocalDate FRIDAY = LocalDate.of(2026, 7, 3);
    private static final LocalDate SATURDAY = FRIDAY.plusDays(1);
    private static final LocalDate SUNDAY = FRIDAY.plusDays(2);
    /** Sunday at 08:30, back at 18:00. */
    private static final Trip TRIP = new Trip(at(SUNDAY, 8, 30), at(SUNDAY, 18, 0), 420, false);
    private static final PlanLevel ASSUMED = new PlanLevel(new Percent(50), Optional.empty());
    /** Saturday evening, the latest start within the charging hours that is done an hour before the departure. */
    private static final Window SATURDAY_EVENING = new Window(at(SATURDAY, 21, 0), at(SATURDAY, 23, 30), 16, 3.7, 0,
            9.2);

    @TempDir
    Path folder;

    private final List<Notice> told = new ArrayList<>();
    private ChargingHours hours = ChargingHours.STANDARD;

    private static Instant at(LocalDate day, int hour, int minute) {
        return day.atTime(hour, minute).atZone(BRUSSELS).toInstant();
    }

    /** The example system, which charges on one phase to a target of 80 %, with a lead time of 15 minutes. */
    private EngineConfig config() {
        try {
            var example = new ConfigFile(Path.of("docs", "example-engine.json")).read();
            var planning = example.planning();
            return example.withPlanning(new PlanningPreferences(planning.minimumPct(), planning.targetPct(),
                    planning.margin(), planning.importToleranceKwh(), planning.minWindow(), Duration.ofMinutes(15),
                    hours));
        } catch (ConfigProblem e) {
            throw new AssertionError(e);
        }
    }

    private static ForecastSource forecast(Instant fetchedAt, Freshness freshness) {
        return new ForecastSource(new ProviderId("main"), "Main", "Forecast by Main", fetchedAt, freshness,
                ProviderRole.PRIMARY, "", Optional.empty());
    }

    /** Kept in the test's folder, so a new announcer is like the app after a restart. */
    private Announcer announcer() {
        return new Announcer(new AnnouncedWindows(folder.resolve("announced.json")),
                new ToldTrips(folder.resolve("trip-notices.json")), told::add);
    }

    /** The trip needs 93.2 %, solar power brings it to 60 %, and the grid charge does the rest. */
    private static TripAdvice withGrid(Window window, TripGridCharge.Start start) {
        return withGrid(window, start, 93.2, 60);
    }

    private static TripAdvice withGrid(Window window, TripGridCharge.Start start, double neededPct,
            double solarLevelPct) {
        return new TripAdvice(TRIP, new Percent(neededPct), List.of(), new Percent(solarLevelPct),
                Optional.of(new TripGridCharge(window, start)), new Percent(neededPct), 0, new Percent(35));
    }

    /** A trip without a grid charge, which leaves with this level. */
    private static TripAdvice withoutGrid(double neededPct, double departurePct, double kmShort) {
        return new TripAdvice(TRIP, new Percent(neededPct), List.of(), new Percent(departurePct), Optional.empty(),
                new Percent(departurePct), kmShort, new Percent(0));
    }

    /** One round with a forecast fetched five minutes ago; what it told. */
    private List<Notice> check(Announcer announcer, TripAdvice advice, PlanLevel level, Instant now) {
        told.clear();
        announcer.announceTripsDue(List.of(advice), level, forecast(now.minus(Duration.ofMinutes(5)),
                Freshness.CACHED), now, config());
        return List.copyOf(told);
    }

    private List<Notice> check(Announcer announcer, TripAdvice advice, Instant now) {
        return check(announcer, advice, ASSUMED, now);
    }

    private static <T extends Notice> List<T> only(Class<T> type, List<Notice> notices) {
        return notices.stream().filter(type::isInstance).map(type::cast).toList();
    }

    @Test
    void theBatteryCheckIsAskedFromTheStartOfTheChargingHoursOnTheDayBeforeTheDeparture() {
        var announcer = announcer();
        var advice = withGrid(SATURDAY_EVENING, TripGridCharge.Start.PLANNED);

        assertThat(check(announcer, advice, at(FRIDAY, 21, 0))).isEmpty();
        assertThat(check(announcer, advice, at(SATURDAY, 6, 59))).isEmpty();
        assertThat(check(announcer, advice, at(SATURDAY, 7, 0)))
                .containsExactly(new BatteryCheck(TRIP, new Percent(50), BRUSSELS));
    }

    @Test
    void aGridChargeOnAnEarlierDayIsAskedAboutOnItsDay() {
        var announcer = announcer();
        var friday = new Window(at(FRIDAY, 21, 0), at(FRIDAY, 23, 30), 16, 3.7, 0, 9.2);
        var advice = withGrid(friday, TripGridCharge.Start.PLANNED);

        assertThat(check(announcer, advice, at(FRIDAY.minusDays(1), 21, 0))).isEmpty();
        assertThat(only(BatteryCheck.class, check(announcer, advice, at(FRIDAY, 7, 0)))).hasSize(1);
    }

    @Test
    void theBatteryCheckWaitsForTheChargingHours() {
        hours = new ChargingHours(LocalTime.of(9, 0), LocalTime.of(20, 0));
        var announcer = announcer();
        var advice = withoutGrid(70, 72, 0);

        assertThat(check(announcer, advice, at(SATURDAY, 8, 59))).isEmpty();
        assertThat(check(announcer, advice, at(SATURDAY, 20, 1))).isEmpty();
        assertThat(check(announcer, advice, at(SATURDAY, 9, 0))).hasSize(1);
    }

    @ParameterizedTest
    @CsvSource({"1, false", "0, true"})
    void aLevelTheOwnerEnteredLatelyIsNotQuestioned(long secondsLater, boolean asked) {
        var now = at(SATURDAY, 8, 0);
        var level = new PlanLevel(new Percent(64), Optional.of(now.minus(TripNotices.RECENT_LEVEL)
                .plusSeconds(secondsLater)));

        var notices = check(announcer(), withoutGrid(70, 72, 0), level, now);

        assertThat(notices).isEqualTo(asked ? List.of(new BatteryCheck(TRIP, new Percent(64), BRUSSELS)) : List.of());
    }

    @Test
    void theBatteryCheckIsAskedOnceAlsoAfterARestart() {
        var advice = withGrid(SATURDAY_EVENING, TripGridCharge.Start.PLANNED);
        var announcer = announcer();
        check(announcer, advice, at(SATURDAY, 7, 0));

        assertThat(check(announcer, advice, at(SATURDAY, 8, 0))).isEmpty();
        assertThat(check(announcer(), advice, at(SATURDAY, 9, 0))).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"9.9, false", "10, true"})
    void theBatteryCheckIsAskedAgainWhenTheTripNeedsMuchMoreFromTheGrid(double morePct, boolean askedAgain) {
        var announcer = announcer();
        check(announcer, withGrid(SATURDAY_EVENING, TripGridCharge.Start.PLANNED), at(SATURDAY, 7, 0));

        var later = withGrid(SATURDAY_EVENING, TripGridCharge.Start.PLANNED, 93.2, 60 - morePct);

        assertThat(only(BatteryCheck.class, check(announcer, later, at(SATURDAY, 8, 0)))).hasSize(askedAgain ? 1 : 0);
    }

    @Test
    void aTripThatNeedsLessFromTheGridThanWhenAskedIsNotAskedAgain() {
        var announcer = announcer();
        check(announcer, withGrid(SATURDAY_EVENING, TripGridCharge.Start.PLANNED), at(SATURDAY, 7, 0));

        var later = withGrid(SATURDAY_EVENING, TripGridCharge.Start.PLANNED, 93.2, 80);

        assertThat(check(announcer, later, at(SATURDAY, 8, 0))).isEmpty();
    }

    /** With a target charge level of 80 %. */
    @ParameterizedTest
    @CsvSource({
        "60, 75, 0, false",     // plenty to spare
        "70, 74.9, 0, true",    // less than 5 points to spare, and solar power did not reach the target
        "70, 75, 0, false",     // 5 points to spare
        "78, 79.9, 0, true",
        "78, 80, 0, false",     // charged to the target: a lower level would take more solar power
        "85, 85, 0, false",     // charged above the target to what the trip needs, which solar power gives
        "100, 100, 41, true"})  // short even when full
    void onlyATripThatDependsOnTheLevelAsksForIt(double neededPct, double departurePct, double kmShort,
            boolean asked) {
        var notices = check(announcer(), withoutGrid(neededPct, departurePct, kmShort), at(SATURDAY, 8, 0));

        assertThat(only(BatteryCheck.class, notices)).hasSize(asked ? 1 : 0);
    }

    @Test
    void thePlannedStartOfATripsGridChargeIsAnnouncedWithinTheLeadTime() {
        var announcer = announcer();
        var advice = withGrid(SATURDAY_EVENING, TripGridCharge.Start.PLANNED);

        assertThat(only(TripChargeStart.class, check(announcer, advice, at(SATURDAY, 20, 44)))).isEmpty();
        assertThat(only(TripChargeStart.class, check(announcer, advice, at(SATURDAY, 20, 45)))).containsExactly(
                new TripChargeStart(TRIP, SATURDAY_EVENING, TripGridCharge.Start.PLANNED, BRUSSELS, Phases.ONE,
                        new Percent(94)));
    }

    @Test
    void aPlannedGridChargeThatHasStartedIsNotAnnounced() {
        var advice = withGrid(SATURDAY_EVENING, TripGridCharge.Start.PLANNED);

        assertThat(only(TripChargeStart.class, check(announcer(), advice, at(SATURDAY, 21, 0)))).isEmpty();
    }

    @Test
    void theStartOfATripsGridChargeIsAnnouncedOnceAlsoWhenItMovesAndAfterARestart() {
        var announcer = announcer();
        check(announcer, withGrid(SATURDAY_EVENING, TripGridCharge.Start.PLANNED), at(SATURDAY, 20, 45));
        var moved = new Window(at(SATURDAY, 21, 5), at(SATURDAY, 23, 35), 16, 3.7, 0, 9.2);

        assertThat(check(announcer, withGrid(moved, TripGridCharge.Start.PLANNED), at(SATURDAY, 20, 50))).isEmpty();
        assertThat(check(announcer(), withGrid(moved, TripGridCharge.Start.NOW), at(SATURDAY, 21, 10))).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"20, 44, false", "20, 45, true", "22, 0, true", "23, 29, true", "23, 30, false"})
    void aGridChargeThatHasToStartNowIsAnnouncedFromTheLeadTimeUntilItsEnd(int hour, int minute, boolean announced) {
        var advice = withGrid(SATURDAY_EVENING, TripGridCharge.Start.NOW);

        var notices = only(TripChargeStart.class, check(announcer(), advice, at(SATURDAY, hour, minute)));

        assertThat(notices).extracting(TripChargeStart::start)
                .isEqualTo(announced ? List.of(TripGridCharge.Start.NOW) : List.of());
    }

    @Test
    void aGridChargeThatHasToStartNowOutsideTheChargingHoursIsAnnouncedAtOnce() {
        var early = new Window(at(SUNDAY, 5, 0), at(SUNDAY, 7, 30), 16, 3.7, 0, 9.2);
        var advice = withGrid(early, TripGridCharge.Start.NOW_OUTSIDE_CHARGING_HOURS);

        var notices = check(announcer(), advice, at(SUNDAY, 5, 10));

        assertThat(notices).containsExactly(new TripChargeStart(TRIP, early,
                TripGridCharge.Start.NOW_OUTSIDE_CHARGING_HOURS, BRUSSELS, Phases.ONE, new Percent(94)));
    }

    @ParameterizedTest
    @CsvSource({"93.2, 94", "94.0000001, 94", "94.01, 95", "100, 100"})
    void theChargeLimitIsWhatTheTripNeedsRoundedUp(double neededPct, double limitPct) {
        var advice = withGrid(SATURDAY_EVENING, TripGridCharge.Start.NOW, neededPct, 60);

        var notices = only(TripChargeStart.class, check(announcer(), advice, at(SATURDAY, 21, 0)));

        assertThat(notices).extracting(TripChargeStart::chargeLimitPct).containsExactly(new Percent(limitPct));
    }

    @Test
    void whenBothAreDueTheLevelIsAskedBeforeTheStart() {
        var notices = check(announcer(), withGrid(SATURDAY_EVENING, TripGridCharge.Start.PLANNED),
                at(SATURDAY, 20, 50));

        assertThat(notices).extracting(notice -> notice.getClass().getSimpleName())
                .containsExactly("BatteryCheck", "TripChargeStart");
    }

    @Test
    void aShortTripIsToldFromTheEveningBeforeTheDepartureOnce() {
        var announcer = announcer();
        var advice = withoutGrid(100, 100, 40.6);

        assertThat(only(TripShort.class, check(announcer, advice, at(SATURDAY, 18, 59)))).isEmpty();
        assertThat(only(TripShort.class, check(announcer, advice, at(SATURDAY, 19, 0))))
                .containsExactly(new TripShort(TRIP, 40.6, BRUSSELS));
        assertThat(check(announcer, advice, at(SATURDAY, 19, 30))).isEmpty();
        assertThat(check(announcer(), advice, at(SATURDAY, 20, 0))).isEmpty();
    }

    @Test
    void aShortTripWaitsForTheChargingHours() {
        hours = new ChargingHours(LocalTime.of(7, 0), LocalTime.of(18, 0));
        var announcer = announcer();
        var advice = withoutGrid(100, 100, 40.6);

        assertThat(only(TripShort.class, check(announcer, advice, at(SATURDAY, 19, 0)))).isEmpty();
        assertThat(only(TripShort.class, check(announcer, advice, at(SUNDAY, 7, 0)))).hasSize(1);
    }

    @Test
    void aTripThatIsNotShortIsNotToldToStop() {
        var notices = check(announcer(), withGrid(SATURDAY_EVENING, TripGridCharge.Start.PLANNED),
                at(SATURDAY, 19, 0));

        assertThat(only(TripShort.class, notices)).isEmpty();
    }

    @Test
    void nothingIsToldOnAForecastThatCouldNotBeRefreshedOrIsOld() {
        var advice = withGrid(SATURDAY_EVENING, TripGridCharge.Start.NOW);
        var now = at(SATURDAY, 21, 0);
        var announcer = announcer();

        announcer.announceTripsDue(List.of(advice), ASSUMED, forecast(now.minusSeconds(60), Freshness.STALE), now,
                config());
        announcer.announceTripsDue(List.of(advice), ASSUMED,
                forecast(now.minus(Announcer.MAX_FORECAST_AGE).minusSeconds(1), Freshness.CACHED), now, config());

        assertThat(told).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"0, true", "1, false"})
    void whatWasToldAboutATripIsKeptUntilADayAfterItsDeparture(long secondsLater, boolean kept) {
        var announcer = announcer();
        check(announcer, withoutGrid(70, 72, 0), at(SATURDAY, 8, 0));
        var later = new Trip(at(SUNDAY.plusDays(2), 9, 0), at(SUNDAY.plusDays(2), 18, 0), 300, false);
        var laterAdvice = new TripAdvice(later, new Percent(70), List.of(), new Percent(72), Optional.empty(),
                new Percent(72), 0, new Percent(20));
        var now = TRIP.departure().plus(TripNotices.KEEP_AFTER_DEPARTURE).plusSeconds(secondsLater);

        check(announcer, laterAdvice, now);

        assertThat(new ToldTrips(folder.resolve("trip-notices.json")).load()).extracting(ToldTrip::departure)
                .isEqualTo(kept ? List.of(TRIP.departure(), later.departure()) : List.of(later.departure()));
    }

    @Test
    void eachNoticeIsLoggedWithTheTripsDeparture() {
        try (var log = LogCapture.of(Announcer.class)) {
            check(announcer(), withoutGrid(70, 72, 0), at(SATURDAY, 8, 0));

            assertThat(log.lines()).containsExactly("INFO told a BatteryCheck for the trip leaving at "
                    + "2026-07-05T08:30+02:00");
        }
    }
}
