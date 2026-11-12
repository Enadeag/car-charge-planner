package nl.enadeag.carchargeplanner.engine.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import nl.enadeag.carchargeplanner.engine.config.Percent;
import nl.enadeag.carchargeplanner.engine.plan.DayPlan;
import nl.enadeag.carchargeplanner.engine.plan.GridAdvice;
import nl.enadeag.carchargeplanner.engine.plan.TripAdvice;
import nl.enadeag.carchargeplanner.engine.plan.TripGridCharge;
import nl.enadeag.carchargeplanner.engine.plan.Window;
import nl.enadeag.carchargeplanner.engine.trip.Trip;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class DayCardTest {

    private static final ZoneId AMSTERDAM = ZoneId.of("Europe/Amsterdam");
    private static final LocalDate TODAY = LocalDate.of(2026, 6, 1);
    private static final LocalDate TOMORROW = TODAY.plusDays(1);
    private static final LocalDate DAY_AFTER = TODAY.plusDays(2);
    private static final Instant MADE_AT = at(TODAY, 8, 0);

    private static Instant at(LocalDate day, int hour, int minute) {
        return day.atTime(hour, minute).atZone(AMSTERDAM).toInstant();
    }

    private static Window window(LocalDate day, int from, int until, double importKwh) {
        return new Window(at(day, from, 0), at(day, until, 0), 10, 2.3, 2.3 * (until - from), importKwh);
    }

    /** A day that starts at 40 %, the level the plan charges up to that day. */
    private static DayPlan day(LocalDate date, List<Window> windows) {
        return levels(date, windows, 40, 60, 55);
    }

    private static DayPlan levels(LocalDate date, List<Window> windows, double start, double after, double end) {
        return new DayPlan(date, new Percent(start), windows, new Percent(after), new Percent(40), new Percent(end), 0,
                List.of());
    }

    /** A day without a window that starts at one level while the plan charges up to another. */
    private static DayPlan chargingTo(LocalDate date, double start, double chargeTo) {
        return new DayPlan(date, new Percent(start), List.of(), new Percent(start), new Percent(chargeTo),
                new Percent(start), 0, List.of());
    }

    private static List<DayPlan> threeDaysWithoutWindows() {
        return List.of(day(TODAY, List.of()), day(TOMORROW, List.of()), day(DAY_AFTER, List.of()));
    }

    private static Trip trip(LocalDate leaves, int hour, LocalDate back, int backHour) {
        return new Trip(at(leaves, hour, 0), at(back, backHour, 0), 300, false);
    }

    private static TripAdvice advice(Trip trip) {
        return new TripAdvice(trip, new Percent(60), List.of(), new Percent(60), Optional.empty(), new Percent(60), 0,
                new Percent(20));
    }

    private static GridAdvice grid(LocalDate day) {
        return new GridAdvice(day, 10, 10, 3, Optional.empty(), Optional.empty());
    }

    private static DashboardView view(Instant madeAt, List<DayPlan> days, boolean assumed, Optional<GridAdvice> grid,
            List<TripAdvice> advice, List<Trip> trips) {
        return DashboardView.of(new PlanParts(madeAt, AMSTERDAM, days, 0.3, new Percent(40), assumed, grid, advice,
                new Percent(20), new Percent(80), List.of(), trips, Parts.source(madeAt)));
    }

    private static List<DashboardDay> days(List<DayPlan> days, Optional<GridAdvice> grid, List<TripAdvice> advice,
            List<Trip> trips) {
        return view(MADE_AT, days, false, grid, advice, trips).days();
    }

    private static List<Optional<NoWindowReason>> reasons(List<DashboardDay> days) {
        return days.stream().map(DashboardDay::noWindow).toList();
    }

    @Test
    void aDayWithWindowsIsAboutTheFirstAndNamesTheOthersAsLater() {
        var first = window(TODAY, 11, 14, 0);
        var second = window(TODAY, 15, 16, 0);
        var third = window(TODAY, 17, 18, 0);

        var day = days(List.of(day(TODAY, List.of(first, second, third))), Optional.empty(), List.of(), List.of())
                .get(0);

        assertThat(day.kind()).isEqualTo(DayKind.SOLAR);
        assertThat(day.main()).contains(new MainCharge(first, OptionalDouble.empty()));
        assertThat(day.later()).containsExactly(second, third);
        assertThat(day.noWindow()).isEmpty();
    }

    @Test
    void aDayWithOneWindowHasNoLaterOnes() {
        var day = days(List.of(day(TODAY, List.of(window(TODAY, 11, 14, 0)))), Optional.empty(), List.of(), List.of())
                .get(0);

        assertThat(day.later()).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"0.05, true", "0.0499999, false", "0, false", "0.69, true"})
    void theMainWindowShowsWhatItTakesFromTheGridFromFiveHundredthsOfAKilowattHourOnly(double importKwh, boolean shown) {
        var main = days(List.of(day(TODAY, List.of(window(TODAY, 11, 14, importKwh)))), Optional.empty(), List.of(),
                List.of()).get(0).main().orElseThrow();

        assertThat(main.gridKwh()).isEqualTo(shown ? OptionalDouble.of(importKwh) : OptionalDouble.empty());
    }

    @Test
    void theAssumedLevelIsNotedOnTodayOnly() {
        var shown = view(MADE_AT, threeDaysWithoutWindows(), true, Optional.empty(), List.of(), List.of()).days();

        assertThat(shown).extracting(DashboardDay::assumedLevel)
                .containsExactly(Optional.of(new Percent(40)), Optional.empty(), Optional.empty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-03-29", "2026-10-25"})
    void todayIsTheHomesDateOnTheDaysTheClocksChange(String date) {
        var changeDay = LocalDate.parse(date);
        var plan = List.of(day(changeDay, List.of()), day(changeDay.plusDays(1), List.of()));

        var shown = view(at(changeDay, 0, 30), plan, true, Optional.empty(), List.of(), List.of()).days();

        assertThat(shown).extracting(DashboardDay::name)
                .containsExactly(new DayName.Today(changeDay), new DayName.Tomorrow(changeDay.plusDays(1)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-03-29", "2026-10-25"})
    void theAssumedLevelIsOnTheHomesDateOnTheDaysTheClocksChange(String date) {
        var changeDay = LocalDate.parse(date);
        var plan = List.of(day(changeDay, List.of()), day(changeDay.plusDays(1), List.of()));

        var shown = view(at(changeDay, 0, 30), plan, true, Optional.empty(), List.of(), List.of()).days();

        assertThat(shown).extracting(DashboardDay::assumedLevel)
                .containsExactly(Optional.of(new Percent(40)), Optional.empty());
    }

    @Test
    void anEnteredLevelIsNotNoted() {
        var shown = view(MADE_AT, threeDaysWithoutWindows(), false, Optional.empty(), List.of(), List.of()).days();

        assertThat(shown).extracting(DashboardDay::assumedLevel).containsOnly(Optional.empty());
    }

    @Test
    void todayIsTheHomesDayAlsoWhenUtcIsStillTheDayBefore() {
        // 00:30 in Amsterdam in summer is 22:30 the day before in UTC
        var justAfterMidnight = at(TODAY, 0, 30);

        var shown = view(justAfterMidnight, threeDaysWithoutWindows(), true, Optional.empty(), List.of(), List.of());

        assertThat(shown.days().get(0).assumedLevel()).contains(new Percent(40));
        assertThat(shown.days().get(1).assumedLevel()).isEmpty();
    }

    @Test
    void aDayWithoutAWindowThatStartsAtWhatThePlanChargesUpToNeedsNone() {
        var shown = days(threeDaysWithoutWindows(), Optional.empty(), List.of(), List.of());

        assertThat(shown).extracting(DashboardDay::kind).containsOnly(DayKind.NONE);
        assertThat(reasons(shown)).containsOnly(Optional.of(NoWindowReason.NOT_NEEDED));
        assertThat(shown).extracting(DashboardDay::main).containsOnly(Optional.empty());
    }

    @ParameterizedTest
    @CsvSource({"80, 80, NOT_NEEDED", "90, 80, NOT_NEEDED", "79.6, 80, NOT_NEEDED", "79.5, 80, NOT_NEEDED",
        "79.4999, 80, TOO_LITTLE_SOLAR", "40, 80, TOO_LITTLE_SOLAR", "80, 80.0000001, NOT_NEEDED",
        "85, 92.1, TOO_LITTLE_SOLAR", "92.4, 92.1, TOO_LITTLE_SOLAR", "92.5, 92.1, NOT_NEEDED",
        "100, 100, NOT_NEEDED", "99.4, 100, TOO_LITTLE_SOLAR", "0, 0, NOT_NEEDED"})
    void aDayWithoutAWindowNeedsNoChargingWhenItsLevelAsShownReachesWhatThePlanChargesUpTo(double start,
            double chargeTo, NoWindowReason reason) {
        // a level is shown rounded half up and a level to charge to rounded up, as the trip advice shows it
        var shown = days(List.of(chargingTo(TODAY, start, chargeTo)), Optional.empty(), List.of(), List.of()).get(0);

        assertThat(shown.noWindow()).contains(reason);
        assertThat(shown.kind()).isEqualTo(DayKind.NONE);
    }

    @Test
    void eachDayIsJudgedByTheLevelItStartsFromNotTheLevelAfterTheGridCharge() {
        // the evening before a trip: its grid charge brings the level after charging up to what the trip needs
        var evening = new DayPlan(TODAY, new Percent(85), List.of(), new Percent(92.1), new Percent(92.1),
                new Percent(92.1), 0, List.of());

        var shown = days(List.of(evening), Optional.empty(), List.of(), List.of()).get(0);

        assertThat(shown.noWindow()).contains(NoWindowReason.TOO_LITTLE_SOLAR);
    }

    private static TripAdvice withGrid(Trip trip, Instant gridStart) {
        var grid = new TripGridCharge(new Window(gridStart, gridStart.plus(Duration.ofHours(2)), 16, 3.7, 7.4, 7.4),
                TripGridCharge.Start.PLANNED);
        return new TripAdvice(trip, new Percent(76.5), List.of(), new Percent(70), Optional.of(grid),
                new Percent(76.5), 0, new Percent(40));
    }

    private static GridAdvice gridWithWindow(LocalDate day, Instant windowStart) {
        var window = new Window(windowStart, windowStart.plus(Duration.ofHours(2)), 16, 3.7, 7.4, 7.4);
        return new GridAdvice(day, 10, 10, 3, Optional.of(window), Optional.of(new Percent(50)));
    }

    @ParameterizedTest
    @CsvSource({"21, GRID_CHARGE_STARTS, NOT_NEEDED", "24, NOT_NEEDED, GRID_CHARGE_STARTS"})
    void theDayATripsGridChargeStartsOnSaysSoThoughItStartsAboveWhatThePlanChargesTo(int startHour,
            NoWindowReason today, NoWindowReason tomorrow) {
        // the trip leaves the day after tomorrow; a grid charge from midnight starts tomorrow, though in UTC it is
        // still 22:00 today
        var leaving = trip(DAY_AFTER, 8, DAY_AFTER, 18);
        var advice = withGrid(leaving, at(TODAY, 0, 0).plus(Duration.ofHours(startHour)));
        var plan = List.of(chargingTo(TODAY, 85, 80), chargingTo(TOMORROW, 85, 80), chargingTo(DAY_AFTER, 85, 80));

        var shown = days(plan, Optional.empty(), List.of(advice), List.of(leaving));

        assertThat(reasons(shown)).containsExactly(Optional.of(today), Optional.of(tomorrow),
                Optional.of(NoWindowReason.DEPARTURE));
    }

    @ParameterizedTest
    @CsvSource({"23, 30, GRID_CHARGE_STARTS, NOT_NEEDED", "24, 0, NOT_NEEDED, GRID_CHARGE_STARTS"})
    void aGridChargesDayIsTheHomesDayAlsoWestOfUtc(int hour, int minute, NoWindowReason today,
            NoWindowReason tomorrow) {
        // 23:30 in New York in summer is 03:30 the next day in UTC
        var newYork = ZoneId.of("America/New_York");
        var madeAt = TODAY.atTime(8, 0).atZone(newYork).toInstant();
        var leaving = new Trip(DAY_AFTER.atTime(8, 0).atZone(newYork).toInstant(),
                DAY_AFTER.atTime(18, 0).atZone(newYork).toInstant(), 300, false);
        var start = TODAY.atStartOfDay(newYork).toInstant().plus(Duration.ofHours(hour).plusMinutes(minute));
        var plan = List.of(chargingTo(TODAY, 85, 80), chargingTo(TOMORROW, 85, 80), chargingTo(DAY_AFTER, 85, 80));

        var shown = DashboardView.of(new PlanParts(madeAt, newYork, plan, 0.3, new Percent(40), false,
                Optional.empty(), List.of(withGrid(leaving, start)), new Percent(20), new Percent(80), List.of(),
                List.of(leaving), Parts.source(madeAt))).days();

        assertThat(reasons(shown)).containsExactly(Optional.of(today), Optional.of(tomorrow),
                Optional.of(NoWindowReason.DEPARTURE));
    }

    @Test
    void theGridAdvicesWindowOnAnEarlierDaySaysAGridChargeStartsThere() {
        // the dip is the day after tomorrow, but its window found room only tomorrow evening
        var plan = List.of(chargingTo(TODAY, 85, 80), chargingTo(TOMORROW, 85, 80), chargingTo(DAY_AFTER, 85, 80));

        var shown = days(plan, Optional.of(gridWithWindow(DAY_AFTER, at(TOMORROW, 20, 0))), List.of(), List.of());

        assertThat(reasons(shown)).containsExactly(Optional.of(NoWindowReason.NOT_NEEDED),
                Optional.of(NoWindowReason.GRID_CHARGE_STARTS), Optional.of(NoWindowReason.GRID));
        assertThat(shown).extracting(DashboardDay::showsBattery).containsExactly(false, true, true);
    }

    @Test
    void theGridAdviceTheDepartureAndBeingAwayComeBeforeAGridChargeThatStarts() {
        // a trip's grid charge starts on the grid advice's day, on its own departure day before it leaves, and on a
        // day another trip keeps the car away
        var away = trip(TODAY.plusDays(3), 7, TODAY.plusDays(4), 18);
        var early = trip(TOMORROW, 9, TOMORROW, 18);
        var late = trip(TODAY.plusDays(5), 8, TODAY.plusDays(5), 18);
        var plan = List.of(chargingTo(TODAY, 40, 80), chargingTo(TOMORROW, 40, 80), chargingTo(DAY_AFTER, 40, 80),
                chargingTo(TODAY.plusDays(3), 40, 80), chargingTo(TODAY.plusDays(4), 40, 80),
                chargingTo(TODAY.plusDays(5), 40, 80));
        var advice = List.of(withGrid(early, at(TOMORROW, 5, 0)), withGrid(late, at(TODAY.plusDays(4), 20, 0)),
                withGrid(away, at(TODAY, 20, 0)));

        var shown = days(plan, Optional.of(grid(TODAY)), advice, List.of(away, early, late));

        assertThat(reasons(shown)).containsExactly(Optional.of(NoWindowReason.GRID),
                Optional.of(NoWindowReason.DEPARTURE), Optional.of(NoWindowReason.TOO_LITTLE_SOLAR),
                Optional.of(NoWindowReason.DEPARTURE), Optional.of(NoWindowReason.AWAY),
                Optional.of(NoWindowReason.DEPARTURE));
    }

    @Test
    void theGridAdviceTheDepartureAndBeingAwayComeBeforeTooLittleSolarPower() {
        var leaving = trip(TOMORROW, 7, DAY_AFTER, 18);
        var plan = List.of(chargingTo(TODAY, 40, 80), chargingTo(TOMORROW, 40, 80), chargingTo(DAY_AFTER, 40, 80));

        var shown = days(plan, Optional.of(grid(TODAY)), List.of(advice(leaving)), List.of(leaving));

        assertThat(reasons(shown)).containsExactly(Optional.of(NoWindowReason.GRID),
                Optional.of(NoWindowReason.DEPARTURE), Optional.of(NoWindowReason.AWAY));
    }

    @Test
    void theCardShowsTheBatteryWithAWindowAndForTheGridAdviceADepartureATripOrAGridChargeThatStartsOnly() {
        var leaving = trip(TOMORROW, 7, DAY_AFTER, 18);
        var later = DAY_AFTER.plusDays(1);
        var last = trip(later.plusDays(4), 8, later.plusDays(4), 18);
        var plan = List.of(chargingTo(TODAY, 40, 80), chargingTo(TOMORROW, 40, 80), chargingTo(DAY_AFTER, 40, 80),
                chargingTo(later, 80, 80), chargingTo(later.plusDays(1), 40, 80),
                day(later.plusDays(2), List.of(window(later.plusDays(2), 11, 14, 0))),
                chargingTo(later.plusDays(3), 80, 80), chargingTo(later.plusDays(4), 80, 80));

        var shown = days(plan, Optional.of(grid(TODAY)),
                List.of(advice(leaving), withGrid(last, at(later.plusDays(3), 20, 0))), List.of(leaving, last));

        assertThat(reasons(shown)).containsExactly(Optional.of(NoWindowReason.GRID),
                Optional.of(NoWindowReason.DEPARTURE), Optional.of(NoWindowReason.AWAY),
                Optional.of(NoWindowReason.NOT_NEEDED), Optional.of(NoWindowReason.TOO_LITTLE_SOLAR), Optional.empty(),
                Optional.of(NoWindowReason.GRID_CHARGE_STARTS), Optional.of(NoWindowReason.DEPARTURE));
        assertThat(shown).extracting(DashboardDay::showsBattery)
                .containsExactly(true, true, true, false, false, true, true, true);
    }

    @Test
    void theAssumedLevelIsNotedOnTodayAlsoWhenItNeedsNoCharging() {
        var plan = List.of(chargingTo(TODAY, 40, 40), chargingTo(TOMORROW, 40, 80));

        var shown = view(MADE_AT, plan, true, Optional.empty(), List.of(), List.of()).days();

        assertThat(reasons(shown)).containsExactly(Optional.of(NoWindowReason.NOT_NEEDED),
                Optional.of(NoWindowReason.TOO_LITTLE_SOLAR));
        assertThat(shown.get(0).assumedLevel()).contains(new Percent(40));
    }

    @Test
    void theGridAdvicesDayIsAGridDayWhateverTheTrips() {
        var leaving = trip(TOMORROW, 7, DAY_AFTER, 18);

        var shown = days(threeDaysWithoutWindows(), Optional.of(grid(TOMORROW)), List.of(advice(leaving)),
                List.of(leaving));

        assertThat(shown).extracting(DashboardDay::kind).containsExactly(DayKind.NONE, DayKind.GRID, DayKind.NONE);
        assertThat(reasons(shown)).containsExactly(Optional.of(NoWindowReason.NOT_NEEDED),
                Optional.of(NoWindowReason.GRID), Optional.of(NoWindowReason.AWAY));
    }

    @Test
    void theGridAdvicesDayWithAWindowIsAboutTheWindow() {
        var shown = days(List.of(day(TODAY, List.of(window(TODAY, 11, 14, 0)))), Optional.of(grid(TODAY)), List.of(),
                List.of());

        assertThat(shown.get(0).kind()).isEqualTo(DayKind.SOLAR);
        assertThat(shown.get(0).noWindow()).isEmpty();
    }

    @Test
    void theDayATripWithAdviceLeavesSaysNothingThoughTheCarIsAwayThatDay() {
        var leaving = trip(TOMORROW, 7, DAY_AFTER, 18);

        var shown = days(threeDaysWithoutWindows(), Optional.empty(), List.of(advice(leaving)), List.of(leaving));

        assertThat(reasons(shown)).containsExactly(Optional.of(NoWindowReason.NOT_NEEDED),
                Optional.of(NoWindowReason.DEPARTURE), Optional.of(NoWindowReason.AWAY));
    }

    @Test
    void departureDaysComeFromTheTripAdviceNotFromTheStoredTrips() {
        var stored = trip(TOMORROW, 7, TOMORROW, 18);
        var advised = trip(DAY_AFTER, 7, DAY_AFTER, 18);

        var shown = days(threeDaysWithoutWindows(), Optional.empty(), List.of(advice(advised)), List.of(stored));

        assertThat(reasons(shown)).containsExactly(Optional.of(NoWindowReason.NOT_NEEDED),
                Optional.of(NoWindowReason.AWAY), Optional.of(NoWindowReason.DEPARTURE));
        assertThat(shown).extracting(day -> day.battery().afterTripPct())
                .containsExactly(Optional.empty(), Optional.empty(), Optional.of(new Percent(55)));
    }

    @Test
    void aTripWithoutThisCarKeepsNobodyAway() {
        var other = new Trip(at(TOMORROW, 7, 0), at(TOMORROW, 18, 0), 300, false, true);

        var shown = days(threeDaysWithoutWindows(), Optional.empty(), List.of(), List.of(other));

        assertThat(reasons(shown)).containsOnly(Optional.of(NoWindowReason.NOT_NEEDED));
    }

    @Test
    void aTripUnderWayKeepsTheCarAwayTodayAndOneBackAtMidnightNotTheNextDay() {
        var underWay = trip(TODAY.minusDays(1), 7, TOMORROW, 0);

        var shown = days(threeDaysWithoutWindows(), Optional.empty(), List.of(), List.of(underWay));

        assertThat(reasons(shown)).containsExactly(Optional.of(NoWindowReason.AWAY),
                Optional.of(NoWindowReason.NOT_NEEDED), Optional.of(NoWindowReason.NOT_NEEDED));
    }

    @Test
    void aTripThatLeftThisMorningKeepsTheCarAwayTodayWithoutAdvice() {
        var underWay = trip(TODAY, 7, TODAY, 18);

        var shown = days(threeDaysWithoutWindows(), Optional.empty(), List.of(), List.of(underWay));

        assertThat(shown.get(0).noWindow()).contains(NoWindowReason.AWAY);
    }

    @Test
    void theDepartureDayIsTheHomesDayAlsoWhenUtcIsStillTheDayBefore() {
        // 01:00 on tomorrow in Amsterdam in summer is 23:00 today in UTC
        var leaving = trip(TOMORROW, 1, DAY_AFTER, 18);

        var shown = days(threeDaysWithoutWindows(), Optional.empty(), List.of(advice(leaving)), List.of(leaving));

        assertThat(reasons(shown)).containsExactly(Optional.of(NoWindowReason.NOT_NEEDED),
                Optional.of(NoWindowReason.DEPARTURE), Optional.of(NoWindowReason.AWAY));
        assertThat(shown).extracting(day -> day.battery().afterTripPct())
                .containsExactly(Optional.empty(), Optional.of(new Percent(55)), Optional.empty());
    }

    @Test
    void aTripLeavingJustAfterMidnightIsAwayFromThatHomeDay() {
        // 00:30 in Amsterdam in summer is 22:30 the day before in UTC
        var leaving = new Trip(at(TOMORROW, 0, 30), at(DAY_AFTER, 18, 0), 300, false);

        var shown = days(threeDaysWithoutWindows(), Optional.empty(), List.of(), List.of(leaving));

        assertThat(reasons(shown)).containsExactly(Optional.of(NoWindowReason.NOT_NEEDED),
                Optional.of(NoWindowReason.AWAY), Optional.of(NoWindowReason.AWAY));
    }

    @Test
    void aTripBackJustAfterMidnightIsAwayThatHomeDay() {
        // 00:30 in Amsterdam in summer is 22:30 the day before in UTC
        var back = new Trip(at(TODAY, 18, 0), at(DAY_AFTER, 0, 30), 300, false);

        var shown = days(threeDaysWithoutWindows(), Optional.empty(), List.of(), List.of(back));

        assertThat(reasons(shown)).containsOnly(Optional.of(NoWindowReason.AWAY));
    }

    @Test
    void aTripThatCanChargeAtItsDestinationStillKeepsTheCarAway() {
        var leaving = new Trip(at(TODAY, 13, 0), at(DAY_AFTER, 19, 0), 300, true);

        var shown = days(threeDaysWithoutWindows(), Optional.empty(), List.of(advice(leaving)), List.of(leaving));

        assertThat(reasons(shown)).containsExactly(Optional.of(NoWindowReason.DEPARTURE),
                Optional.of(NoWindowReason.AWAY), Optional.of(NoWindowReason.AWAY));
    }

    @Test
    void aDayWithAWindowIsTheSameWhateverTheTrips() {
        var leaving = trip(TODAY, 18, TODAY, 22);
        var plan = List.of(day(TODAY, List.of(window(TODAY, 11, 14, 0))));

        var shown = days(plan, Optional.empty(), List.of(advice(leaving)), List.of(leaving)).get(0);

        assertThat(shown.kind()).isEqualTo(DayKind.SOLAR);
        assertThat(shown.noWindow()).isEmpty();
        assertThat(shown.battery().afterTripPct()).contains(new Percent(55));
    }

    @Test
    void theBatteryIsAtTheStartAndAfterChargingAndOnlyTheDayATripLeavesAfterTheTrip() {
        var leaving = trip(TOMORROW, 7, TOMORROW, 18);
        var plan = List.of(levels(TODAY, List.of(), 40, 60, 55), levels(TOMORROW, List.of(), 60, 74, 30));

        var shown = days(plan, Optional.empty(), List.of(advice(leaving)), List.of(leaving));

        assertThat(shown).extracting(DashboardDay::battery).containsExactly(
                new BatteryLevels(new Percent(40), new Percent(60), Optional.empty()),
                new BatteryLevels(new Percent(60), new Percent(74), Optional.of(new Percent(30))));
    }

    @ParameterizedTest
    @CsvSource({"50, 50, false", "49.5, 50.4, false", "50, 50.4999, false", "50, 50.5, true", "49.4999, 49.5, true",
        "49.5, 50.5, true", "0, 0.4, false", "99.5, 100, false", "74, 50, true"})
    void theBatteryChargesBeforeTheTripWhenTheLevelsDifferInWholePercentRoundedHalfUp(double start, double after,
            boolean charges) {
        var leaving = trip(TODAY, 18, TODAY, 22);
        var plan = List.of(levels(TODAY, List.of(), start, after, 30));

        var shown = days(plan, Optional.empty(), List.of(advice(leaving)), List.of(leaving)).get(0).battery();

        assertThat(shown.chargesBeforeTrip()).isEqualTo(charges);
    }

    @Test
    void theDashboardHasThePlansMomentZoneAndChargeLevelsOnce() {
        var shown = view(MADE_AT, threeDaysWithoutWindows(), false, Optional.empty(), List.of(), List.of());

        assertThat(shown.madeAt()).isEqualTo(MADE_AT);
        assertThat(shown.zone()).isEqualTo(AMSTERDAM);
        assertThat(shown.levels()).isEqualTo(new ChargeLevels(new Percent(20), new Percent(80)));
    }
}
