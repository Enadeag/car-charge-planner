package nl.enadeag.carchargeplanner.engine.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import nl.enadeag.carchargeplanner.engine.config.Percent;
import nl.enadeag.carchargeplanner.engine.plan.DayPlan;
import nl.enadeag.carchargeplanner.engine.plan.GridAdvice;
import nl.enadeag.carchargeplanner.engine.plan.TripAdvice;
import nl.enadeag.carchargeplanner.engine.plan.TripGridCharge;
import nl.enadeag.carchargeplanner.engine.plan.TripGridCharge.Start;
import nl.enadeag.carchargeplanner.engine.plan.Window;
import nl.enadeag.carchargeplanner.engine.trip.Trip;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

/** The trip advice as the dashboard shows it: what the plan does, its source, its flags, and where it shows. */
class DashboardTripAdviceTest {

    private static final ZoneId BRUSSELS = ZoneId.of("Europe/Brussels");
    /** A Friday. */
    private static final LocalDate TODAY = LocalDate.of(2026, 7, 3);
    private static final LocalDate TOMORROW = TODAY.plusDays(1);
    private static final Instant MADE_AT = at(TODAY, 10, 20);
    /** Tomorrow from 08:15 to 19:00, 150 km. */
    private static final Trip TRIP = new Trip(at(TOMORROW, 8, 15), at(TOMORROW, 19, 0), 150, false);
    private static final Window SOLAR = new Window(at(TODAY, 12, 0), at(TODAY, 15, 0), 8, 1.8, 5.2, 0.1);

    private static Instant at(LocalDate day, int hour, int minute) {
        return day.atTime(hour, minute).atZone(BRUSSELS).toInstant();
    }

    /** A grid charge of 16 A on one phase, from a start to an hour later. */
    private static TripGridCharge grid(Instant start, Start when) {
        return new TripGridCharge(new Window(start, start.plusSeconds(3600), 16, 3.7, 0, 3.7), when);
    }

    private static TripAdvice advice(Trip trip, double neededPct, List<Window> solar, Optional<TripGridCharge> grid,
            double departurePct, double kmShort) {
        return new TripAdvice(trip, new Percent(neededPct), solar, new Percent(60), grid, new Percent(departurePct),
                kmShort, new Percent(Math.max(0, departurePct - 52)));
    }

    private static TripAdvice advice(double neededPct, List<Window> solar, Optional<TripGridCharge> grid,
            double departurePct, double kmShort) {
        return advice(TRIP, neededPct, solar, grid, departurePct, kmShort);
    }

    private static TripAdvice gridAt(Instant start) {
        return advice(90, List.of(), Optional.of(grid(start, Start.PLANNED)), 90, 0);
    }

    /** Three days from the day {@code madeAt} falls on, an assumed level of 45% and a target of 80%. */
    private static DashboardView dashboard(Instant madeAt, boolean assumed, Optional<GridAdvice> gridAdvice,
            List<TripAdvice> advice) {
        var first = madeAt.atZone(BRUSSELS).toLocalDate();
        var days = Stream.of(first, first.plusDays(1), first.plusDays(2))
                .map(date -> new DayPlan(date, new Percent(45), List.of(), new Percent(45), new Percent(45), 0,
                        List.of()))
                .toList();
        return DashboardView.of(new PlanParts(madeAt, BRUSSELS, days, 0.3, new Percent(45), assumed, gridAdvice,
                advice, new Percent(30), new Percent(80), List.of()));
    }

    private static TripAdviceView view(Instant madeAt, TripAdvice advice) {
        return dashboard(madeAt, true, Optional.empty(), List.of(advice)).tripAdvice().get(0);
    }

    private static TripAdviceView view(TripAdvice advice) {
        return view(MADE_AT, advice);
    }

    private static TripGridStart start(TripAdviceView view) {
        var plan = view.plan();
        return plan instanceof TripPlan.Grid ? ((TripPlan.Grid) plan).grid() : ((TripPlan.SolarThenGrid) plan).grid();
    }

    @Test
    void aTripChargedOnSolarPowerAndThenFromTheGridHasBothPartsTheStartAndTheChargeLimitRoundedUp() {
        var grid = grid(at(TODAY, 21, 0), Start.PLANNED);

        var view = view(advice(84.2, List.of(SOLAR), Optional.of(grid), 84.2, 0));

        assertThat(view).isEqualTo(new TripAdviceView(TRIP, new DayName.Tomorrow(TOMORROW),
                new TripPlan.SolarThenGrid(new Percent(85), List.of(new SolarDay(new DayName.Today(TODAY),
                        List.of(SOLAR))), new TripGridStart(grid.window(), Start.PLANNED, new DayName.Today(TODAY),
                        false, true)),
                OptionalDouble.empty(), new Percent(45), true));
        assertThat(view.source()).isEqualTo(EnergySource.GRID);
        assertThat(view.asksAction()).isTrue();
    }

    @Test
    void aTripChargedFromTheGridAloneHasNoSolarPartAndNamesTheDayOfTheStart() {
        var grid = grid(at(TOMORROW, 7, 0), Start.PLANNED);

        var view = view(advice(90, List.of(), Optional.of(grid), 90, 0));

        assertThat(view.plan()).isEqualTo(new TripPlan.Grid(new Percent(90), new TripGridStart(grid.window(),
                Start.PLANNED, new DayName.Tomorrow(TOMORROW), false, false)));
        assertThat(view.source()).isEqualTo(EnergySource.GRID);
    }

    @ParameterizedTest
    @CsvSource({"0, true", "-60, true", "1, false", "14400, false"})
    void aGridChargeStartsNowUpToAndIncludingTheMomentThePlanWasMade(long secondsAfter, boolean startsNow) {
        var view = view(gridAt(MADE_AT.plusSeconds(secondsAfter)));

        assertThat(start(view).startsNow()).isEqualTo(startsNow);
    }

    @ParameterizedTest
    @CsvSource({"12, 0, false", "17, 59, false", "18, 0, true", "21, 0, true", "23, 59, true"})
    void aStartLaterTodayIsTonightFromSixInTheEvening(int hour, int minute, boolean tonight) {
        var view = view(gridAt(at(TODAY, hour, minute)));

        assertThat(start(view).tonight()).isEqualTo(tonight);
    }

    @ParameterizedTest
    @CsvSource({"0", "-1800"})
    void aStartDueNowIsNotTonightEvenInTheEvening(long secondsAfter) {
        var evening = at(TODAY, 19, 0);

        var start = start(view(evening, gridAt(evening.plusSeconds(secondsAfter))));

        assertThat(start.startsNow()).isTrue();
        assertThat(start.tonight()).isFalse();
    }

    @ParameterizedTest
    @CsvSource({"0, 30", "18, 0", "21, 0"})
    void aStartOnTheNextDayIsNeverTonight(int hour, int minute) {
        var start = start(view(gridAt(at(TOMORROW, hour, minute))));

        assertThat(start.tonight()).isFalse();
        assertThat(start.day()).isEqualTo(new DayName.Tomorrow(TOMORROW));
    }

    @Test
    void theDaysAreNamedFromTheDayThePlanWasMadeInTheHomesZone() {
        // 22:30 in UTC on the 3rd is 00:30 on the 4th in Brussels: the trip leaves today and 21:00 is tonight
        var lateAtNight = Instant.parse("2026-07-03T22:30:00Z");

        var view = view(lateAtNight, gridAt(at(TOMORROW, 21, 0)));

        assertThat(view.departureDay()).isEqualTo(new DayName.Today(TOMORROW));
        assertThat(start(view).day()).isEqualTo(new DayName.Today(TOMORROW));
        assertThat(start(view).tonight()).isTrue();
    }

    @Test
    void theDepartureDayIsTheHomesNotTheInstantsUtcDay() {
        // 00:30 in Brussels is still the day before in UTC
        var early = new Trip(at(TOMORROW, 0, 30), at(TOMORROW, 6, 0), 40, false);

        var view = view(advice(early, 70, List.of(), Optional.empty(), 75, 0));

        assertThat(view.departureDay()).isEqualTo(new DayName.Tomorrow(TOMORROW));
    }

    @ParameterizedTest
    @CsvSource({"40.2, 40.2", "0.001, 0.001", "120, 120"})
    void aTripTheBatteryFallsShortForKeepsTheKmShortAsTheyAre(double kmShort, double shortKm) {
        var view = view(advice(100, List.of(SOLAR), Optional.of(grid(at(TODAY, 21, 0), Start.PLANNED)), 100,
                kmShort));

        assertThat(view.shortKm()).isEqualTo(OptionalDouble.of(shortKm));
        assertThat(view.source()).isEqualTo(EnergySource.GRID);
    }

    @ParameterizedTest
    @CsvSource({"0", "-0.5"})
    void aTripTheBatteryHoldsIsNotShort(double kmShort) {
        var view = view(advice(90, List.of(), Optional.of(grid(at(TODAY, 21, 0), Start.PLANNED)), 90, kmShort));

        assertThat(view.shortKm()).isEmpty();
    }

    @Test
    void aTripTheCarCannotChargeForBeforeItLeavesHasTheLevelItLeavesWithAndHowFarItFallsShort() {
        var view = view(advice(95, List.of(), Optional.empty(), 60, 120));

        assertThat(view.plan()).isEqualTo(new TripPlan.Leaves(new Percent(60)));
        assertThat(view.shortKm()).isEqualTo(OptionalDouble.of(120));
        assertThat(view.source()).isEqualTo(EnergySource.GRID);
        assertThat(view.asksAction()).isTrue();
    }

    @Test
    void solarPowerAboveTheTargetHasItsWindowsByDayInOrder() {
        var later = new Window(at(TODAY, 16, 0), at(TODAY, 17, 30), 6, 1.4, 2.0, 0);
        var tomorrow = new Window(at(TOMORROW, 6, 30), at(TOMORROW, 7, 15), 6, 1.4, 0.6, 0.1);

        var view = view(advice(93.6, List.of(SOLAR, later, tomorrow), Optional.empty(), 94, 0));

        assertThat(view.plan()).isEqualTo(new TripPlan.Solar(new Percent(94), List.of(
                new SolarDay(new DayName.Today(TODAY), List.of(SOLAR, later)),
                new SolarDay(new DayName.Tomorrow(TOMORROW), List.of(tomorrow)))));
        assertThat(view.source()).isEqualTo(EnergySource.SOLAR);
        assertThat(view.asksAction()).isTrue();
    }

    @Test
    void aSolarWindowIsOnTheDayOfItsStartInTheHomesZone() {
        // 00:30 in Brussels is still the day before in UTC
        var early = new Window(at(TOMORROW, 0, 30), at(TOMORROW, 1, 0), 6, 1.4, 0.7, 0);

        var view = view(advice(93.6, List.of(SOLAR, early), Optional.empty(), 94, 0));

        assertThat(((TripPlan.Solar) view.plan()).days()).extracting(SolarDay::day)
                .containsExactly(new DayName.Today(TODAY), new DayName.Tomorrow(TOMORROW));
    }

    @Test
    void solarPowerThatFallsShortComesFromTheGrid() {
        var view = view(advice(100, List.of(SOLAR), Optional.empty(), 94, 12.5));

        assertThat(view.plan()).isInstanceOf(TripPlan.Solar.class);
        assertThat(view.source()).isEqualTo(EnergySource.GRID);
    }

    static Stream<Arguments> tripsThatNeedNothingExtra() {
        return Stream.of(
                // the plan's own windows up to the target are enough
                Arguments.of(advice(70, List.of(SOLAR), Optional.empty(), 75, 0)),
                // exactly the target is what the plan charges to anyway
                Arguments.of(advice(80, List.of(SOLAR), Optional.empty(), 80, 0)),
                // above the target, but the battery holds it already
                Arguments.of(advice(88, List.of(), Optional.empty(), 91, 0)));
    }

    @ParameterizedTest
    @MethodSource("tripsThatNeedNothingExtra")
    void aTripThatNeedsNothingExtraIsEnoughFromTheCar(TripAdvice advice) {
        var view = view(advice);

        assertThat(view.plan()).isEqualTo(new TripPlan.Enough(advice.departureLevelPct()));
        assertThat(view.source()).isEqualTo(EnergySource.CAR);
        assertThat(view.asksAction()).isFalse();
        assertThat(view.shortKm()).isEmpty();
    }

    @Test
    void solarPowerJustAboveTheTargetIsNotEnough() {
        var view = view(advice(80.01, List.of(SOLAR), Optional.empty(), 81, 0));

        assertThat(view.plan()).isInstanceOf(TripPlan.Solar.class);
    }

    @Test
    void aLevelOnlyAFractionAboveAWholePercentageIsNotRoundedUpToTheNext() {
        var view = view(advice(85 + 1e-9, List.of(), Optional.of(grid(at(TODAY, 21, 0), Start.PLANNED)), 85, 0));

        assertThat(((TripPlan.Grid) view.plan()).chargeLimitPct()).isEqualTo(new Percent(85));
    }

    @ParameterizedTest
    @CsvSource({"true", "false"})
    void theAdviceStartsFromThePlansBatteryLevelAndSaysWhetherItIsAssumed(boolean assumed) {
        var view = dashboard(MADE_AT, assumed, Optional.empty(), List.of(gridAt(at(TODAY, 21, 0)))).tripAdvice()
                .get(0);

        assertThat(view.levelPct()).isEqualTo(new Percent(45));
        assertThat(view.levelAssumed()).isEqualTo(assumed);
    }

    @Test
    void everyTripGetsItsAdviceInOrder() {
        var sunday = new Trip(at(TODAY.plusDays(2), 9, 0), at(TODAY.plusDays(2), 12, 0), 40, true);
        var first = advice(70, List.of(), Optional.empty(), 75, 0);
        var second = advice(sunday, 50, List.of(), Optional.empty(), 70, 0);

        var advice = dashboard(MADE_AT, true, Optional.empty(), List.of(first, second)).tripAdvice();

        assertThat(advice).extracting(TripAdviceView::trip).containsExactly(TRIP, sunday);
    }

    @Test
    void theAdviceShowsWithTheDayOfTheDepartureAndWithTodayOnlyWhenItAsksSomething() {
        var asks = view(gridAt(at(TODAY, 21, 0)));
        var enough = view(advice(70, List.of(), Optional.empty(), 75, 0));

        assertThat(asks.shownOn(new DayName.Tomorrow(TOMORROW))).isTrue();
        assertThat(asks.shownOn(new DayName.Today(TODAY))).isTrue();
        assertThat(asks.shownOn(new DayName.Other(TODAY.plusDays(2)))).isFalse();
        assertThat(enough.shownOn(new DayName.Tomorrow(TOMORROW))).isTrue();
        assertThat(enough.shownOn(new DayName.Today(TODAY))).isFalse();
    }

    @Test
    void aTripThatLeavesTodayShowsWithTodayAlsoWhenItAsksNothing() {
        var today = new Trip(at(TODAY, 15, 0), at(TODAY, 18, 0), 40, false);

        var view = view(advice(today, 70, List.of(), Optional.empty(), 75, 0));

        assertThat(view.shownOn(new DayName.Today(TODAY))).isTrue();
    }

    @Test
    void eachDayShowsTheAdviceOfTheTripsThatLeaveThatDayAndTodayAlsoThoseThatAskInTheEnginesOrder() {
        var leavingToday = new Trip(at(TODAY, 15, 0), at(TODAY, 18, 0), 40, false);
        var later = new Trip(at(TOMORROW, 20, 0), at(TOMORROW, 23, 0), 40, false);
        var dayAfter = new Trip(at(TODAY.plusDays(2), 9, 0), at(TODAY.plusDays(2), 12, 0), 40, false);
        var far = new Trip(at(TODAY.plusDays(5), 9, 0), at(TODAY.plusDays(5), 12, 0), 40, false);
        var grid = Optional.of(grid(at(TODAY, 12, 0), Start.PLANNED));
        var dashboard = dashboard(MADE_AT, true, Optional.empty(), List.of(
                advice(leavingToday, 90, List.of(), grid, 90, 0), advice(90, List.of(), grid, 90, 0),
                advice(later, 70, List.of(), Optional.empty(), 75, 0), advice(dayAfter, 90, List.of(), grid, 90, 0),
                advice(far, 90, List.of(), grid, 90, 0)));

        var shown = dashboard.days().stream()
                .map(day -> dashboard.tripAdviceOn(day).stream().map(TripAdviceView::trip).toList())
                .toList();

        assertThat(shown).containsExactly(List.of(leavingToday, TRIP, dayAfter, far), List.of(TRIP, later),
                List.of(dayAfter));
    }

    @Test
    void theGridAdviceShowsWithTodayAndNoOtherDay() {
        var advice = new GridAdvice(TODAY.plusDays(2), 12, 18, 7.4, Optional.empty(), Optional.empty());
        var dashboard = dashboard(MADE_AT, true, Optional.of(advice), List.of());

        assertThat(dashboard.days()).extracting(dashboard::gridAdviceOn)
                .containsExactly(dashboard.gridAdvice(), Optional.empty(), Optional.empty());
    }

    @Test
    void aPlanWhoseFirstDayIsNotTodayShowsTheGridAdviceWithNoDay() {
        var advice = new GridAdvice(TODAY.plusDays(2), 12, 18, 7.4, Optional.empty(), Optional.empty());
        var shown = dashboard(MADE_AT, true, Optional.of(advice), List.of());
        var later = new DashboardView(shown.madeAt(), shown.zone(), shown.days().subList(1, 3), shown.levels(),
                shown.gridAdvice(), shown.tripAdvice());

        assertThat(later.days()).extracting(later::gridAdviceOn).containsOnly(Optional.empty());
    }

    @Test
    void withoutGridAdviceNoDayShowsOne() {
        var dashboard = dashboard(MADE_AT, true, Optional.empty(), List.of());

        assertThat(dashboard.gridAdviceOn(dashboard.days().get(0))).isEmpty();
    }
}
