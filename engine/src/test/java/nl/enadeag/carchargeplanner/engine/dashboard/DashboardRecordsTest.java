package nl.enadeag.carchargeplanner.engine.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import nl.enadeag.carchargeplanner.engine.config.Percent;
import nl.enadeag.carchargeplanner.engine.plan.DayPlan;
import nl.enadeag.carchargeplanner.engine.plan.TripAdvice;
import nl.enadeag.carchargeplanner.engine.plan.TripGridCharge;
import nl.enadeag.carchargeplanner.engine.plan.Window;
import nl.enadeag.carchargeplanner.engine.trip.Trip;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class DashboardRecordsTest {

    private static final StartLevel LEVEL = new StartLevel(new Percent(45), true);

    private static final Instant NOON = Instant.parse("2026-06-01T10:00:00Z");
    private static final LocalDate DAY = LocalDate.of(2026, 6, 1);

    @ParameterizedTest
    @CsvSource({"50, 50, false", "49.5, 50, false", "50, 50.4, false", "49.4999, 50, true", "49.4, 50, true",
        "50, 50.5, true", "40, 60, true"})
    void aDayChargesBeforeTheTripExactlyWhenTheWholePercentagesDiffer(double start, double after, boolean charges) {
        var levels = new BatteryLevels(new Percent(start), new Percent(after), Optional.empty());

        assertThat(levels.chargesBeforeTrip()).isEqualTo(charges);
    }

    private static DayChart chart(double from, double to, double scale, double baseload) {
        return new DayChart(from, to, scale, List.of(), baseload, List.of(), Optional.empty(), List.of(),
                Optional.empty());
    }

    @Test
    void aChartWithinItsRangesIsMade() {
        var chart = chart(0, 0.5, 0.1, 0);

        assertThat(chart.fromHour()).isEqualTo(0);
        assertThat(chart.baseloadKw()).isEqualTo(0);
    }

    @ParameterizedTest
    @CsvSource({"-1, 20, 1, 0.3", "6, 6, 1, 0.3", "NaN, 20, 1, 0.3", "6, NaN, 1, 0.3", "6, 20, 0, 0.3",
        "6, 20, NaN, 0.3", "6, 20, Infinity, 0.3", "6, 20, 1, -0.1", "6, 20, 1, NaN", "6, 20, 1, Infinity"})
    void aChartRefusesHoursThatDoNotRunForwardsFromZeroAndAScaleOrBaseloadOutOfRange(double from, double to,
            double scale, double baseload) {
        assertThatThrownBy(() -> chart(from, to, scale, baseload)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aChartKeepsItsOwnLists() {
        var steps = new ArrayList<>(List.of(new DayChart.Step(6, 7, 1, NOON)));
        var chart = new DayChart(6, 20, 1, steps, 0.3, List.of(), Optional.empty(), List.of(), Optional.empty());

        steps.clear();

        assertThat(chart.steps()).hasSize(1);
    }

    @ParameterizedTest
    @CsvSource({"2, 2, 1", "3, 2, 1", "2, 3, -0.1", "2, 3, NaN",
        "2, 3, Infinity", "2, 3, -Infinity"})
    void aStepRunsForwardsAndItsPowerIsFiniteAndNotNegative(double start, double end, double kw) {
        assertThatThrownBy(() -> new DayChart.Step(start, end, kw, NOON)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aStepWithoutPowerIsMade() {
        assertThat(new DayChart.Step(2, 2.25, 0, NOON).kw()).isEqualTo(0);
    }

    @ParameterizedTest
    @CsvSource({"3, 3", "4, 3", "NaN, 3"})
    void aWindowRunsForwards(double start, double end) {
        assertThatThrownBy(() -> new DayChart.WindowBand(start, end, NOON, NOON))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @CsvSource({"0", "-1", "NaN", "Infinity"})
    void aPeakHasPower(double kw) {
        assertThatThrownBy(() -> new DayChart.Peak(kw, NOON)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @CsvSource({"-0.5", "NaN"})
    void aMomentIsNotBeforeTheStartOfTheDay(double hour) {
        assertThatThrownBy(() -> new DayChart.Moment(hour, NOON)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aMomentAtTheStartOfTheDayIsMade() {
        assertThat(new DayChart.Moment(0, NOON).hour()).isEqualTo(0);
    }

    @ParameterizedTest
    @CsvSource({"-0.5, 3", "NaN, 3", "1, -1", "1, 25"})
    void aTickIsNotBeforeTheStartOfTheDayAndItsClockHourIsFrom0To24(double hour, int clockHour) {
        assertThatThrownBy(() -> new DayChart.Tick(hour, clockHour, NOON)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @CsvSource({"0, 0", "24, 24"})
    void aTickAtTheEdgesOfTheClockIsMade(double hour, int clockHour) {
        assertThat(new DayChart.Tick(hour, clockHour, NOON).clockHour()).isEqualTo(clockHour);
    }

    private static final Window WINDOW = new Window(NOON, NOON.plusSeconds(3600), 10, 2.3, 2.3, 0);
    private static final Optional<MainCharge> MAIN = Optional.of(new MainCharge(WINDOW, OptionalDouble.empty()));
    private static final BatteryLevels BATTERY = new BatteryLevels(new Percent(40), new Percent(50), Optional.empty());

    private static DashboardDay day(Optional<MainCharge> main, List<Window> later, Optional<NoWindowReason> noWindow) {
        return new DashboardDay(new DayName.Today(DAY), main, later, noWindow, BATTERY, Optional.empty(),
                chart(6, 20, 1, 0.3));
    }

    @Test
    void aDashboardKeepsItsOwnDays() {
        var days = new ArrayList<>(List.of(day(MAIN, List.of(), Optional.empty())));
        var dashboard = new DashboardView(NOON, ZoneId.of("Europe/Amsterdam"), days,
                new ChargeLevels(new Percent(20), new Percent(80)), LEVEL, Optional.empty(), List.of(),
                Parts.footer(NOON), List.of(), List.of());

        days.clear();

        assertThat(dashboard.days()).hasSize(1);
    }

    @Test
    void aDayKeepsItsOwnLaterWindows() {
        var later = new ArrayList<>(List.of(WINDOW));
        var day = day(MAIN, later, Optional.empty());

        later.clear();

        assertThat(day.later()).containsExactly(WINDOW);
    }

    @Test
    void aDayWithAMainWindowKeepsItAndIsASolarDay() {
        var day = day(MAIN, List.of(WINDOW), Optional.empty());

        assertThat(day.main()).isEqualTo(MAIN);
        assertThat(day.kind()).isEqualTo(DayKind.SOLAR);
    }

    @Test
    void aDayWithoutAWindowBecauseOfTheGridAdviceIsAGridDay() {
        assertThat(day(Optional.empty(), List.of(), Optional.of(NoWindowReason.GRID)).kind()).isEqualTo(DayKind.GRID);
    }

    @ParameterizedTest
    @EnumSource(value = NoWindowReason.class, names = "GRID", mode = EnumSource.Mode.EXCLUDE)
    void aDayWithoutAWindowForAnotherReasonIsOfNoKind(NoWindowReason reason) {
        assertThat(day(Optional.empty(), List.of(), Optional.of(reason)).kind()).isEqualTo(DayKind.NONE);
    }

    @ParameterizedTest
    @EnumSource(NoWindowReason.class)
    void aDayWithoutAWindowKeepsItsReason(NoWindowReason reason) {
        assertThat(day(Optional.empty(), List.of(), Optional.of(reason)).noWindow()).contains(reason);
    }

    @Test
    void aDayWithoutAMainWindowHasNoLaterOnes() {
        assertThatThrownBy(() -> day(Optional.empty(), List.of(WINDOW),
                Optional.of(NoWindowReason.NOT_NEEDED))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aDayHasAReasonWithoutAWindowExactlyWhenItHasNoWindow() {
        assertThatThrownBy(() -> day(MAIN, List.of(), Optional.of(NoWindowReason.NOT_NEEDED)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> day(Optional.empty(), List.of(), Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void onlyTodayHasAnAssumedLevel() {
        var tomorrow = DAY.plusDays(1);

        assertThatThrownBy(() -> new DashboardDay(new DayName.Tomorrow(tomorrow), MAIN, List.of(), Optional.empty(),
                BATTERY, Optional.of(new Percent(40)), chart(6, 20, 1, 0.3)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new DashboardDay(new DayName.Today(DAY), MAIN, List.of(), Optional.empty(), BATTERY,
                Optional.of(new Percent(40)), chart(6, 20, 1, 0.3)).assumedLevel()).contains(new Percent(40));
    }

    @ParameterizedTest
    @CsvSource({"0", "-0.1", "NaN", "Infinity"})
    void theMainWindowsGridEnergyIsFiniteAndMoreThanZero(double kwh) {
        assertThatThrownBy(() -> new MainCharge(WINDOW, OptionalDouble.of(kwh))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theMainWindowTakesALittleGridEnergyOrNone() {
        assertThat(new MainCharge(WINDOW, OptionalDouble.of(0.01)).gridKwh()).hasValue(0.01);
        assertThat(new MainCharge(WINDOW, OptionalDouble.empty()).gridKwh()).isEmpty();
    }

    @Test
    void theChargeLevelsRefuseAMinimumAboveTheTargetButTakeOneEqualToIt() {
        assertThatThrownBy(() -> new ChargeLevels(new Percent(81), new Percent(80)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new ChargeLevels(new Percent(80), new Percent(80)).minimumPct()).isEqualTo(new Percent(80));
    }

    @Test
    void thePartsKeepTheirOwnLists() {
        var trip = new Trip(NOON, NOON.plusSeconds(3600), 10, false);
        var trips = new ArrayList<>(List.of(trip));
        var advice = new ArrayList<>(List.of(new TripAdvice(trip, new Percent(60), List.of(), new Percent(60),
                Optional.empty(), new Percent(60), 0, new Percent(20))));
        var parts = new PlanParts(NOON, ZoneId.of("Europe/Amsterdam"), List.<DayPlan>of(), 0.3, new Percent(40),
                false, Optional.empty(), advice, new Percent(20), new Percent(80), List.of(), trips,
                Parts.source(NOON));

        trips.clear();
        advice.clear();

        assertThat(parts.trips()).hasSize(1);
        assertThat(parts.tripAdvice()).hasSize(1);
    }

    @ParameterizedTest
    @CsvSource({"2026-06-01T23:30:00Z, Europe/Amsterdam, 2026-06-02",
        "2026-06-02T02:30:00Z, America/New_York, 2026-06-01", "2026-06-01T12:00:00Z, UTC, 2026-06-01"})
    void todayIsTheDayThePlanWasMadeOnInTheHomesTimeZone(Instant madeAt, ZoneId zone, LocalDate today) {
        assertThat(Parts.of(madeAt, zone, List.of(), 0.3).today()).isEqualTo(today);
    }

    private static GridAdviceView gridAdvice(double energyKwh, double endLevelPct) {
        return new GridAdviceView(new DayName.Tomorrow(DAY.plusDays(1)), energyKwh, endLevelPct, Optional.empty());
    }

    @ParameterizedTest
    @CsvSource({"0", "-0.1", "NaN", "Infinity"})
    void theGridAdvicesEnergyIsFiniteAndMoreThanZero(double kwh) {
        assertThatThrownBy(() -> gridAdvice(kwh, 10)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @CsvSource({"NaN", "Infinity", "-Infinity"})
    void theGridAdvicesEndLevelIsFinite(double endLevelPct) {
        assertThatThrownBy(() -> gridAdvice(6, endLevelPct)).isInstanceOf(IllegalArgumentException.class);
    }

    private static DashboardView dashboardWith(GridAdviceView advice) {
        return new DashboardView(NOON, ZoneId.of("Europe/Amsterdam"), List.of(),
                new ChargeLevels(new Percent(30), new Percent(80)), LEVEL, Optional.of(advice), List.of(),
                Parts.footer(NOON), List.of(), List.of());
    }

    @ParameterizedTest
    @CsvSource({"30", "30.1", "100"})
    void theDashboardRefusesAGridAdviceThatEndsAtOrAboveTheMinimum(double endLevelPct) {
        assertThatThrownBy(() -> dashboardWith(gridAdvice(6, endLevelPct)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theDashboardTakesAGridAdviceThatEndsJustBelowTheMinimum() {
        var advice = gridAdvice(6, 29.99);

        assertThat(dashboardWith(advice).gridAdvice()).contains(advice);
    }

    @ParameterizedTest
    @CsvSource({"0.01, 29.99", "6, -250"})
    void theGridAdviceTakesALittleEnergyAndAnEndLevelJustBelowTheMinimumOrFarBelowNothing(double kwh,
            double endLevelPct) {
        var advice = gridAdvice(kwh, endLevelPct);

        assertThat(advice.energyKwh()).isEqualTo(kwh);
        assertThat(advice.endLevelPct()).isEqualTo(endLevelPct);
    }

    @ParameterizedTest
    @CsvSource({"0, true", "-0.0, true", "-0.01, true", "-250, true", "0.01, false", "29, false"})
    void theGridAdviceRunsEmptyExactlyWhenTheDayEndsAtOrBelowNothing(double endLevelPct, boolean runsEmpty) {
        assertThat(gridAdvice(6, endLevelPct).runsEmpty()).isEqualTo(runsEmpty);
    }

    @Test
    void theGridAdvicesWindowIsOnItsDayOrBeforeItAndNotAfter() {
        var limit = new Percent(50);
        var tomorrow = new DayName.Tomorrow(DAY.plusDays(1));
        var sameDay = new GridWindow(WINDOW, tomorrow, limit);
        var before = new GridWindow(WINDOW, new DayName.Today(DAY), limit);
        var after = new GridWindow(WINDOW, new DayName.Other(DAY.plusDays(2)), limit);

        assertThat(new GridAdviceView(tomorrow, 6, 10, Optional.of(sameDay)).window())
                .contains(sameDay);
        assertThat(new GridAdviceView(tomorrow, 6, 10, Optional.of(before)).window())
                .contains(before);
        assertThatThrownBy(() -> new GridAdviceView(tomorrow, 6, 10, Optional.of(after)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static final Trip TRIP = new Trip(NOON.plusSeconds(86_400), NOON.plusSeconds(90_000), 150, false);
    private static final TripPlan GRID_PLAN = new TripPlan.Grid(new Percent(90),
            new TripGridStart(WINDOW, TripGridCharge.Start.PLANNED, new DayName.Today(DAY), TripGridStart.When.LATER));

    private static TripAdviceView tripAdvice(TripPlan plan, OptionalDouble shortKm) {
        return new TripAdviceView(TRIP, new DayName.Tomorrow(DAY.plusDays(1)), plan, shortKm);
    }

    @ParameterizedTest
    @CsvSource({"0", "-0.1", "NaN", "Infinity"})
    void theTripAdvicesDistanceShortIsFiniteAndMoreThanZero(double km) {
        assertThatThrownBy(() -> tripAdvice(GRID_PLAN, OptionalDouble.of(km)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theTripAdviceTakesADistanceShortJustAboveZero() {
        assertThat(tripAdvice(GRID_PLAN, OptionalDouble.of(0.001)).shortKm()).hasValue(0.001);
    }

    @Test
    void aTripPlanThatChargesEnoughIsNeverShort() {
        var enough = new TripPlan.Enough(new Percent(75));

        assertThat(tripAdvice(enough, OptionalDouble.empty()).plan()).isEqualTo(enough);
        assertThatThrownBy(() -> tripAdvice(enough, OptionalDouble.of(10)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aTripPlanThatOnlyLeavesIsAlwaysShort() {
        var leaves = new TripPlan.Leaves(new Percent(60));

        assertThat(tripAdvice(leaves, OptionalDouble.of(10)).plan()).isEqualTo(leaves);
        assertThatThrownBy(() -> tripAdvice(leaves, OptionalDouble.empty()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aDayOfSolarWindowsHasAtLeastOneAndKeepsItsOwnList() {
        var windows = new ArrayList<>(List.of(WINDOW));
        var day = new SolarDay(new DayName.Today(DAY), windows);

        windows.clear();

        assertThat(day.windows()).containsExactly(WINDOW);
        assertThatThrownBy(() -> new SolarDay(new DayName.Today(DAY), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aSolarTripPlanHasSolarDaysAndKeepsItsOwnList() {
        var days = new ArrayList<>(List.of(new SolarDay(new DayName.Today(DAY), List.of(WINDOW))));
        var solar = new TripPlan.Solar(new Percent(90), days);
        var grid = ((TripPlan.Grid) GRID_PLAN).grid();
        var solarThenGrid = new TripPlan.SolarThenGrid(new Percent(90), days, grid);

        days.clear();

        assertThat(solar.days()).hasSize(1);
        assertThat(solarThenGrid.days()).hasSize(1);
        assertThatThrownBy(() -> new TripPlan.Solar(new Percent(90), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TripPlan.SolarThenGrid(new Percent(90), List.of(), grid))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @CsvSource({"NOW, true, false", "TONIGHT, false, true", "LATER, false, false"})
    void aGridStartIsDueNowOrTonightExactlyAsItsWhenSays(TripGridStart.When when, boolean startsNow, boolean tonight) {
        var start = new TripGridStart(WINDOW, TripGridCharge.Start.PLANNED, new DayName.Today(DAY), when);

        assertThat(start.startsNow()).isEqualTo(startsNow);
        assertThat(start.tonight()).isEqualTo(tonight);
    }

    @Test
    void onlyAGridStartOnTheDayThePlanWasMadeIsTonight() {
        assertThatThrownBy(() -> new TripGridStart(WINDOW, TripGridCharge.Start.PLANNED,
                new DayName.Tomorrow(DAY.plusDays(1)), TripGridStart.When.TONIGHT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aDashboardKeepsItsOwnTripAdvice() {
        var advice = new ArrayList<>(List.of(tripAdvice(GRID_PLAN, OptionalDouble.empty())));
        var dashboard = new DashboardView(NOON, ZoneId.of("Europe/Amsterdam"), List.of(),
                new ChargeLevels(new Percent(20), new Percent(80)), LEVEL, Optional.empty(), advice,
                Parts.footer(NOON), List.of(), List.of());

        advice.clear();

        assertThat(dashboard.tripAdvice()).hasSize(1);
    }
}
