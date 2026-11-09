package nl.enadeag.carchargeplanner.engine.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import nl.enadeag.carchargeplanner.engine.config.Percent;
import nl.enadeag.carchargeplanner.engine.plan.DayPlan;
import nl.enadeag.carchargeplanner.engine.plan.TripAdvice;
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
                new ChargeLevels(new Percent(20), new Percent(80)));

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
                false, Optional.empty(), advice, new Percent(20), new Percent(80), trips);

        trips.clear();
        advice.clear();

        assertThat(parts.trips()).hasSize(1);
        assertThat(parts.tripAdvice()).hasSize(1);
    }
}
