package nl.enadeag.carchargeplanner.engine.plan;

import static nl.enadeag.carchargeplanner.engine.plan.Forecasts.PREFERENCES;
import static nl.enadeag.carchargeplanner.engine.plan.Forecasts.WEEKLY_AWAY;
import static nl.enadeag.carchargeplanner.engine.plan.Forecasts.at;
import static nl.enadeag.carchargeplanner.engine.plan.Forecasts.bellDay;
import static nl.enadeag.carchargeplanner.engine.plan.Forecasts.bellDays;
import static nl.enadeag.carchargeplanner.engine.plan.Forecasts.hourly;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assertions.within;

import nl.enadeag.carchargeplanner.engine.away.OneOffAwayPeriod;
import nl.enadeag.carchargeplanner.engine.config.Charger;
import nl.enadeag.carchargeplanner.engine.config.HomeSystem;
import nl.enadeag.carchargeplanner.engine.config.Percent;
import nl.enadeag.carchargeplanner.engine.config.Phases;
import nl.enadeag.carchargeplanner.engine.config.PlanningPreferences;
import nl.enadeag.carchargeplanner.engine.forecast.ForecastStep;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PlannerTest {

    private static final LocalDate SUMMER_FRI = LocalDate.of(2026, 7, 3);
    /** 23 hours: 02:00 winter time becomes 03:00 summer time. */
    private static final LocalDate SPRING_FORWARD = LocalDate.of(2026, 3, 29);
    /** 25 hours: 03:00 summer time becomes 02:00 winter time. */
    private static final LocalDate FALL_BACK = LocalDate.of(2026, 10, 25);
    private static final Offset<Double> CLOSE = within(1e-6);

    // after the 0.6 kW baseload and the 0.85 margin: 1.6 kW leaves 0.85 kW (3 A, not chargeable), 2.6 kW leaves
    // 1.7 kW (7 A) and 4.4 kW leaves 3.23 kW: 14 A (3.22 kW), while 15 A would import 0.22 kWh an hour
    private static final Map<Integer, Double> SUNNY =
            Map.of(8, 1.6, 9, 2.6, 10, 4.4, 11, 4.4, 12, 4.4, 13, 4.4, 14, 4.4, 15, 2.6);
    private static final Map<Integer, Double> QUIET = Map.of(9, 1.6, 10, 1.6, 11, 1.6, 12, 1.6, 13, 1.6, 14, 1.6, 15, 1.6);

    private record Span(Instant start, Instant end, int amps) {
    }

    private static List<Span> spans(DayPlan day) {
        return day.windows().stream().map(w -> new Span(w.start(), w.end(), w.amps())).toList();
    }

    private static List<DayPlan> plan(List<ForecastStep> forecast, HomeSystem system, double soc, Instant now) {
        return new Planner(system, PREFERENCES, WEEKLY_AWAY).plan(forecast, new Percent(soc), now, List.of());
    }

    /** The example system, with its weekly away period and this one entered by hand. */
    private static List<DayPlan> planAway(List<ForecastStep> forecast, double soc, Instant now, OneOffAwayPeriod oneOff) {
        var away = new AwayTimes(WEEKLY_AWAY.weekly(), List.of(oneOff), List.of());
        return new Planner(Forecasts.example(), PREFERENCES, away).plan(forecast, new Percent(soc), now, List.of());
    }

    /** A bell-shaped Friday for the example system, planned from midnight or the given hour. */
    private static DayPlan friday(double peakWm2, double soc, int nowHour) {
        return plan(bellDay(SUMMER_FRI, peakWm2), Forecasts.example(), soc, at(SUMMER_FRI, nowHour)).getFirst();
    }

    private static DayPlan friday(double peakWm2, double soc) {
        return friday(peakWm2, soc, 0);
    }

    @Test
    void eachDayHoldsTheForecastStepsThatStartOnItInTimeOrder() {
        var forecast = new ArrayList<>(bellDays(Map.of(SUMMER_FRI, 800.0, SUMMER_FRI.plusDays(1), 600.0)));
        Collections.reverse(forecast);

        var days = plan(forecast, Forecasts.example(), 50, at(SUMMER_FRI, 9));

        assertThat(days).hasSize(2);
        for (var day : days) {
            assertThat(day.forecast()).hasSize(24).isSortedAccordingTo(Comparator.comparing(ForecastStep::start))
                    .allSatisfy(step -> assertThat(step.start().atZone(Forecasts.TZ).toLocalDate()).isEqualTo(day.date()));
        }
        // the step from 23:00 to midnight is the day's last, not the next day's first
        assertThat(days.getFirst().forecast().getLast().end()).isEqualTo(SUMMER_FRI.plusDays(1).atStartOfDay(Forecasts.TZ).toInstant());
    }

    @Test
    void aClockChangeDayHoldsItsOwnNumberOfHours() {
        var days = plan(bellDay(FALL_BACK, 500), Forecasts.example(), 50, at(FALL_BACK, 0));

        assertThat(days.getFirst().forecast()).hasSize(25);
    }

    @Test
    void clearSummerDayGivesOneLongWindowNearTheSurplusPeak() {
        var day = friday(900, 40);
        // the usable surplus peaks at about 2.6 kW: six hours at 9 A (2.07 kW) take a little from the grid at the edges
        assertThat(spans(day)).containsExactly(new Span(at(SUMMER_FRI, 9), at(SUMMER_FRI, 15), 9));
        var window = day.windows().getFirst();
        assertThat(window.powerKw()).isEqualTo(2.07);
        assertThat(window.solarKwh()).isCloseTo(12.270693, within(1e-6));
        assertThat(window.importKwh()).isCloseTo(0.149307, within(1e-6));
        assertThat(window.solarKwh() + window.importKwh()).isCloseTo(2.07 * 6, CLOSE);
    }

    @Test
    void goldenSunnyDay() {
        var day = plan(hourly(SUMMER_FRI, SUNNY), Forecasts.golden(), 62, at(SUMMER_FRI, 0)).getFirst();
        // need 18 % of 70 kWh / 0.9 = 14 kWh: 12.88 kWh by 14:00, the rest in 1.12 / 3.22 h = 21 min, rounded up to 14:25
        assertThat(spans(day)).containsExactly(new Span(at(SUMMER_FRI, 10), at(SUMMER_FRI, 14, 25), 14));
        var window = day.windows().getFirst();
        assertThat(window.powerKw()).isEqualTo(3.22);
        assertThat(window.importKwh()).isZero();
        assertThat(window.solarKwh()).isCloseTo(3.22 * 53 / 12, CLOSE);
        // 14.22 kWh * 0.9 would reach 80.3 %: capped at the 80 % target
        assertThat(day.levelAfterChargingPct().value()).isCloseTo(80, CLOSE);
        assertThat(day.drivingPct()).isCloseTo(18, CLOSE);
        assertThat(day.endLevelPct().value()).isCloseTo(62, CLOSE);
    }

    @Test
    void goldenThreeDaysWithQuietDaysCarryingTheLevelOver() {
        var saturday = SUMMER_FRI.plusDays(1);
        var sunday = SUMMER_FRI.plusDays(2);
        var forecast = new ArrayList<ForecastStep>();
        forecast.addAll(hourly(SUMMER_FRI, QUIET));
        forecast.addAll(hourly(saturday, SUNNY));
        forecast.addAll(hourly(sunday, QUIET));
        var days = plan(forecast, Forecasts.golden(), 62, at(SUMMER_FRI, 0));

        assertThat(days).extracting(DayPlan::date).containsExactly(SUMMER_FRI, saturday, sunday);
        assertThat(days.get(0).windows()).isEmpty();
        assertThat(days.get(2).windows()).isEmpty();
        // Saturday needs 36 % of 70 kWh / 0.9 = 28 kWh, more than the five hours at 3.22 kW give
        assertThat(spans(days.get(1))).containsExactly(new Span(at(saturday, 10), at(saturday, 15), 14));
        assertThat(days.get(1).windows().getFirst().solarKwh()).isCloseTo(16.1, CLOSE);
        // 16.1 kWh * 0.9 / 70 kWh = 20.7 %
        var levels = days.stream()
                .flatMap(d -> java.util.stream.Stream.of(d.startLevelPct().value(), d.levelAfterChargingPct().value(), d.endLevelPct().value()))
                .toList();
        var expected = List.of(62.0, 62.0, 44.0, 44.0, 64.7, 46.7, 46.7, 46.7, 28.7);
        IntStream.range(0, expected.size())
                .forEach(i -> assertThat(levels.get(i)).as("level %d", i).isCloseTo(expected.get(i), CLOSE));
    }

    @Test
    void goldenSunnyDayOnThreePhases() {
        // 8.1 kW leaves 6.375 kW: 9 A on three phases (6.21 kW); 10 A (6.9 kW) imports too much and the 1.7 kW of the
        // 2.6 kW hours is below the 6 A minimum of 4.14 kW
        var forecast = hourly(SUMMER_FRI, Map.of(9, 2.6, 10, 8.1, 11, 8.1, 12, 8.1, 13, 8.1, 14, 8.1, 15, 2.6));
        var day = plan(forecast, Forecasts.goldenOnThreePhases(), 62, at(SUMMER_FRI, 0)).getFirst();
        // 14 kWh: 12.42 kWh by 12:00, the rest in 1.58 / 6.21 h = 15.3 min, rounded up to 12:20
        assertThat(spans(day)).containsExactly(new Span(at(SUMMER_FRI, 10), at(SUMMER_FRI, 12, 20), 9));
        var window = day.windows().getFirst();
        assertThat(window.powerKw()).isEqualTo(6.21);
        assertThat(window.importKwh()).isZero();
        assertThat(window.solarKwh()).isCloseTo(6.21 * 7 / 3, CLOSE);
        assertThat(day.levelAfterChargingPct().value()).isCloseTo(80, CLOSE);
    }

    @Test
    void overcastDayHasNoWindow() {
        var day = friday(150, 40);
        assertThat(day.windows()).isEmpty();
        assertThat(day.levelAfterChargingPct().value()).isEqualTo(40);
    }

    @Test
    void surplusBelowTheMinimumPowerIsNotChargeable() {
        // peak surplus about 1.0 kW, below the 1.38 kW of 6 A
        double peak = (1.0 / 0.85 + 0.6) / (4.8 * (1 - 0.14)) * 1000;
        assertThat(friday(peak, 40).windows()).isEmpty();
    }

    @Test
    void ampsAreRoundedDownAndCappedAtTheChargersMaximum() {
        var system = Forecasts.example();
        var step = new ForecastStep(at(SUMMER_FRI, 12), at(SUMMER_FRI, 13), 3.7);
        double usable = Charging.usableSurplusKw(step, system, PREFERENCES);
        assertThat(Charging.ampsFor(usable, system.charger())).isEqualTo((int) (usable * 1000 / 230));
        assertThat(Charging.ampsFor(50, system.charger())).isEqualTo(32);
    }

    @Test
    void theWeeklyAwayPeriodOnThursdayLeavesThatDayWithoutAWindow() {
        var thursday = SUMMER_FRI.minusDays(1);
        var day = plan(bellDay(thursday, 900), Forecasts.example(), 40, at(thursday, 0)).getFirst();
        assertThat(day.windows()).isEmpty();
    }

    @Test
    void theThursdayAwayPeriodLeavesFridayAlone() {
        assertThat(spans(friday(900, 40))).containsExactly(new Span(at(SUMMER_FRI, 9), at(SUMMER_FRI, 15), 9));
    }

    @Test
    void awayUntilTheAfternoonLeavesOnlyALaterWindow() {
        var away = new OneOffAwayPeriod(at(SUMMER_FRI, 0), at(SUMMER_FRI, 14));
        var windows = planAway(bellDay(SUMMER_FRI, 900), 40, at(SUMMER_FRI, 0), away).getFirst().windows();
        assertThat(windows).extracting(Window::start, Window::end, Window::amps)
                .containsExactly(tuple(at(SUMMER_FRI, 14), at(SUMMER_FRI, 16), 7));
    }

    @Test
    void awayAllDayLeavesThatDayWithoutAWindowAndTheNextDayAlone() {
        var forecast = bellDays(Map.of(SUMMER_FRI, 900.0, SUMMER_FRI.plusDays(1), 900.0));
        var days = planAway(forecast, 40, at(SUMMER_FRI, 7), OneOffAwayPeriod.allDay(SUMMER_FRI, Forecasts.TZ));
        assertThat(days.get(0).windows()).isEmpty();
        var saturday = SUMMER_FRI.plusDays(1);
        assertThat(spans(days.get(1))).containsExactly(new Span(at(saturday, 9), at(saturday, 15), 9));
    }

    @Test
    void awayForPartOfTheDayLeavesTheRestOfIt() {
        var away = OneOffAwayPeriod.on(SUMMER_FRI, LocalTime.of(0, 0), LocalTime.of(13, 0), Forecasts.TZ);
        var windows = planAway(bellDay(SUMMER_FRI, 900), 40, at(SUMMER_FRI, 7), away).getFirst().windows();
        assertThat(windows).extracting(Window::start, Window::end, Window::amps)
                .containsExactly(tuple(at(SUMMER_FRI, 13), at(SUMMER_FRI, 16), 7));
    }

    @Test
    void windowIsTrimmedToTheEnergyNeeded() {
        var day = friday(900, 75);
        double need = new Planner(Forecasts.example(), PREFERENCES, WEEKLY_AWAY).energyNeededKwh(new Percent(75));
        assertThat(need).isCloseTo(5.0 / 100 * 70 / 0.9, CLOSE);
        // every current from 6 A up covers the need from 08:00 in the sun; the earliest start, then the most amps
        // that still take nothing from the grid: 3.89 kWh at 1.38 kW is 2 h 49 min, rounded up to 10:50
        assertThat(spans(day)).containsExactly(new Span(at(SUMMER_FRI, 8), at(SUMMER_FRI, 10, 50), 6));
        assertThat(day.windows().getFirst().solarKwh()).isCloseTo(1.38 * 17 / 6, CLOSE);
        assertThat(day.levelAfterChargingPct().value()).isCloseTo(80, CLOSE);
    }

    @Test
    void shortNeedStillGetsTheMinimumWindowLength() {
        // 0.2 % of 70 kWh / 0.9 = 0.16 kWh: 7 minutes at 6 A, stretched to the 45-minute minimum
        var day = friday(900, 79.8);
        assertThat(spans(day)).containsExactly(new Span(at(SUMMER_FRI, 8), at(SUMMER_FRI, 8, 45), 6));
        assertThat(day.windows().getFirst().solarKwh()).isCloseTo(1.38 * 0.75, CLOSE);
    }

    @Test
    void noWindowWhenTheBatteryIsAtOrAboveTheTarget() {
        var day = friday(900, 85);
        assertThat(day.windows()).isEmpty();
        assertThat(day.levelAfterChargingPct().value()).isEqualTo(85);
    }

    @Test
    void aHigherMarginGivesMorePower() {
        var cautious = friday(900, 40).windows().getFirst();
        var bold = new Planner(Forecasts.example(), new PlanningPreferences(new Percent(30), new Percent(80), 1.0, 0.2,
                Duration.ofMinutes(45), Duration.ofMinutes(15)),
                WEEKLY_AWAY)
                .plan(bellDay(SUMMER_FRI, 900), new Percent(40), at(SUMMER_FRI, 0), List.of()).getFirst().windows().getFirst();
        assertThat(List.of(cautious.amps(), bold.amps())).containsExactly(9, 10);
        assertThat(bold.powerKw()).isEqualTo(2.3);
        assertThat(bold.solarKwh()).isCloseTo(2.3 * 6, CLOSE);
    }

    @ParameterizedTest
    @ValueSource(doubles = {300, 500, 700, 900, 1000})
    void expectedImportStaysWithinTheTolerance(double peakWm2) {
        assertThat(friday(peakWm2, 30).windows())
                .allSatisfy(w -> assertThat(w.importKwh()).isLessThanOrEqualTo(PREFERENCES.importToleranceKwh() + 1e-6));
    }

    @Test
    void springForwardWindowAcrossTheSwitchCountsRealHours() {
        // sun at night is artificial: it puts the window across the switch. Keys 1-3 are 01:00 winter time, 03:00, 04:00
        var forecast = hourly(SPRING_FORWARD, Map.of(1, 4.4, 2, 4.4, 3, 4.4));
        // need 10.35 % of 70 kWh / 0.9 = 8.05 kWh: 2.5 real hours at 14 A (3.22 kW), 3.5 hours on the clock
        var day = plan(forecast, Forecasts.golden(), 69.65, at(SPRING_FORWARD, 0)).getFirst();
        assertThat(spans(day)).containsExactly(new Span(at(SPRING_FORWARD, 1), at(SPRING_FORWARD, 4, 30), 14));
        assertThat(day.windows().getFirst().importKwh()).isZero();
        assertThat(day.windows().getFirst().solarKwh()).isCloseTo(8.05, CLOSE);
        assertThat(day.levelAfterChargingPct().value()).isCloseTo(80, CLOSE);
    }

    @Test
    void springForwardDayPlannedFromMidnightTakesAWholeDaysDriving() {
        var day = plan(hourly(SPRING_FORWARD, Map.of()), Forecasts.golden(), 60, at(SPRING_FORWARD, 0)).getFirst();
        assertThat(day.drivingPct()).isCloseTo(18, CLOSE);
        assertThat(day.endLevelPct().value()).isCloseTo(42, CLOSE);
    }

    @Test
    void fallBackDayPlansTheAfternoonByLocalTimeAndOneDaysDriving() {
        // keys count real hours, so after the repeated 02:00 hour key 11 is 10:00 local time
        var forecast = hourly(FALL_BACK, Map.of(10, 2.6, 11, 4.4, 12, 4.4, 13, 4.4, 14, 4.4, 15, 4.4, 16, 2.6));
        var day = plan(forecast, Forecasts.golden(), 62, at(FALL_BACK, 0)).getFirst();
        assertThat(spans(day)).containsExactly(new Span(at(FALL_BACK, 10), at(FALL_BACK, 14, 25), 14));
        assertThat(day.windows().getFirst().solarKwh()).isCloseTo(3.22 * 53 / 12, CLOSE);
        assertThat(day.levelAfterChargingPct().value()).isCloseTo(80, CLOSE);
        assertThat(day.drivingPct()).isCloseTo(18, CLOSE);
        assertThat(day.endLevelPct().value()).isCloseTo(62, CLOSE);
    }

    @Test
    void fallBackWindowAcrossTheRepeatedHourCountsRealHours() {
        // keys 1-3 are 01:00 summer time, 02:00 summer time and 02:00 winter time
        var forecast = hourly(FALL_BACK, Map.of(1, 4.4, 2, 4.4, 3, 4.4));
        // need 8.05 kWh: 2.5 real hours at 3.22 kW, so from 01:00 summer time (23:00 UTC) to 02:30 winter time (01:30 UTC)
        var day = plan(forecast, Forecasts.golden(), 69.65, at(FALL_BACK, 0)).getFirst();
        assertThat(spans(day)).containsExactly(
                new Span(Instant.parse("2026-10-24T23:00:00Z"), Instant.parse("2026-10-25T01:30:00Z"), 14));
        assertThat(day.windows().getFirst().solarKwh()).isCloseTo(8.05, CLOSE);
        assertThat(day.levelAfterChargingPct().value()).isCloseTo(80, CLOSE);
    }

    @Test
    void runAcrossMidnightIsOneWindowThatBelongsToTheDayItStarts() {
        var start = at(SUMMER_FRI, 22);
        var forecast = IntStream.range(0, 6)
                .mapToObj(h -> new ForecastStep(start.plus(Duration.ofHours(h)), start.plus(Duration.ofHours(h + 1)), 3.3))
                .toList();
        var finder = new WindowFinder(Forecasts.example(), PREFERENCES);
        var runs = finder.runs(forecast, start, List.of());
        assertThat(runs).hasSize(1);
        assertThat(WindowFinder.byDay(runs, Forecasts.TZ)).containsOnlyKeys(SUMMER_FRI);
        var window = finder.windows(runs, 100).getFirst();
        assertThat(window.start()).isEqualTo(start);
        assertThat(window.end()).isEqualTo(start.plus(Duration.ofHours(6)));
    }

    @Test
    void nowCutsOffTheFirstStep() {
        assertThat(spans(friday(900, 40, 12))).containsExactly(new Span(at(SUMMER_FRI, 12), at(SUMMER_FRI, 16), 7));
    }

    @Test
    void theDrivingOfADayIsTakenOffBeforeTheNext() {
        var friday = SUMMER_FRI;
        var saturday = SUMMER_FRI.plusDays(1);
        var days = plan(bellDays(Map.of(friday, 150.0, saturday, 150.0)), Forecasts.example(), 60, at(friday, 0));
        double driving = 600.0 / 7 * 18 / 100 / 70 * 100;
        assertThat(days.get(0).endLevelPct().value()).isCloseTo(60 - driving, CLOSE);
        assertThat(days.get(1).startLevelPct().value()).isCloseTo(days.get(0).endLevelPct().value(), within(1e-9));
    }

    @Test
    void aSunnyDayAfterADullDayChargesFromTheLowerLevel() {
        var saturday = SUMMER_FRI.plusDays(1);
        var days = plan(bellDays(Map.of(SUMMER_FRI, 150.0, saturday, 900.0)), Forecasts.example(), 60, at(SUMMER_FRI, 0));
        assertThat(days.get(0).windows()).isEmpty();
        assertThat(spans(days.get(1))).containsExactly(new Span(at(saturday, 9), at(saturday, 15), 9));
        assertThat(days.get(1).startLevelPct().value()).isCloseTo(60 - 600.0 / 7 * 18 / 100 / 70 * 100, CLOSE);
    }

    @Test
    void aSurplusThatOnePhaseCanUseIsTooSmallForThree() {
        // about 3 kW of surplus: 13 A on one phase, but under the 4.1 kW that 6 A on three phases already takes
        var forecast = bellDay(SUMMER_FRI, 1000);
        assertThat(spans(plan(forecast, Forecasts.exampleOnPhases(Phases.ONE), 40, at(SUMMER_FRI, 0)).getFirst()))
                .containsExactly(new Span(at(SUMMER_FRI, 9), at(SUMMER_FRI, 15), 10));
        assertThat(plan(forecast, Forecasts.exampleOnPhases(Phases.THREE), 40, at(SUMMER_FRI, 0)).getFirst().windows()).isEmpty();
    }

    @Test
    void aThreePhasePlanNeverGoesBelowTheThreePhaseMinimum() {
        var system = Forecasts.exampleOnPhases(Phases.THREE);
        var charger = system.charger();
        // 2500 W/m2 at the peak is an artificial sky: about 10 kW, enough for three phases
        var windows = plan(bellDay(SUMMER_FRI, 2500), system, 40, at(SUMMER_FRI, 0)).getFirst().windows();
        assertThat(windows).extracting(Window::start, Window::end, Window::amps)
                .containsExactly(tuple(at(SUMMER_FRI, 8), at(SUMMER_FRI, 14, 30), 7));
        assertThat(windows).allSatisfy(window -> {
            assertThat(window.powerKw()).isGreaterThanOrEqualTo(Charging.powerKw(charger.minAmps(), charger) - 1e-9);
            assertThat(window.powerKw()).isCloseTo(Charging.powerKw(window.amps(), charger), CLOSE);
            assertThat(window.amps()).isBetween(charger.minAmps(), charger.maxAmps());
        });
    }

    @Test
    void thePlanHasOneDayForEachForecastDay() {
        var saturday = SUMMER_FRI.plusDays(1);
        var days = plan(bellDays(Map.of(SUMMER_FRI, 900.0, saturday, 900.0)), Forecasts.example(), 40, at(SUMMER_FRI, 0));
        assertThat(days).extracting(DayPlan::date).containsExactly(SUMMER_FRI, saturday);
    }

    /** The Friday from 40 %, planned at the hour, with these windows announced. */
    private static DayPlan fridayWithAnnounced(int nowHour, double soc, List<Window> announced) {
        return new Planner(Forecasts.example(), PREFERENCES, WEEKLY_AWAY)
                .plan(bellDay(SUMMER_FRI, 900), new Percent(soc), at(SUMMER_FRI, nowHour), announced).getFirst();
    }

    /** The window the owner was told about at 07:00. */
    private static Window announced() {
        return friday(900, 40, 7).windows().getFirst();
    }

    @Test
    void withoutAnnouncingTheRunningWindowWouldDrift() {
        // why announced windows are kept: planning again at 12:00 gives another power and end
        var announced = announced();
        var later = friday(900, 40, 12).windows().getFirst();
        assertThat(new Span(announced.start(), announced.end(), announced.amps()))
                .isEqualTo(new Span(at(SUMMER_FRI, 9), at(SUMMER_FRI, 15), 9));
        assertThat(new Span(later.start(), later.end(), later.amps()))
                .isEqualTo(new Span(at(SUMMER_FRI, 12), at(SUMMER_FRI, 16), 7));
    }

    @ParameterizedTest
    @ValueSource(ints = {9, 10, 12, 14})
    void anAnnouncedWindowKeepsItsPowerAndEndWhileItRuns(int nowHour) {
        var announced = announced();
        var day = fridayWithAnnounced(nowHour, 40, List.of(announced));
        assertThat(spans(day)).containsExactly(new Span(announced.start(), announced.end(), announced.amps()));
    }

    @Test
    void anAnnouncedWindowCountsOnlyItsRemainingEnergy() {
        var announced = announced();
        var day = fridayWithAnnounced(12, 40, List.of(announced));
        double share = Hours.between(at(SUMMER_FRI, 12), announced.end()) / Hours.between(announced.start(), announced.end());
        assertThat(day.windows().getFirst().solarKwh()).isCloseTo(announced.solarKwh() * share, within(1e-9));
        assertThat(spans(day)).containsExactly(new Span(announced.start(), announced.end(), announced.amps()));
        assertThat(day.levelAfterChargingPct().value())
                .isCloseTo(40 + announced.solarKwh() * share * 0.9 / 70 * 100, within(1e-9));
    }

    @Test
    void aBatteryAboveTheTargetKeepsItsLevelWhileAnAnnouncedWindowRuns() {
        // the window still gives sun, but the level after charging is never below the start nor raised past it
        var announced = announced();
        var day = fridayWithAnnounced(12, 85, List.of(announced));
        assertThat(spans(day)).containsExactly(new Span(announced.start(), announced.end(), announced.amps()));
        assertThat(day.levelAfterChargingPct().value()).isEqualTo(85);
    }

    @Test
    void noSecondWindowIsPlannedOnTopOfAnAnnouncedOne() {
        var announced = announced();
        var day = fridayWithAnnounced(11, 10, List.of(announced));
        // the battery is far from the target, but the sun after the window touches it and so belongs to it
        assertThat(spans(day)).containsExactly(new Span(announced.start(), announced.end(), announced.amps()));
    }

    @Test
    void anAnnouncedWindowThatIsOverOrHasNotStartedIsIgnored() {
        var announced = announced();
        assertThat(fridayWithAnnounced(7, 40, List.of(announced)).windows())
                .isEqualTo(fridayWithAnnounced(7, 40, List.of()).windows());
        assertThat(fridayWithAnnounced(16, 40, List.of(announced)).windows()).doesNotContain(announced);
    }

    /** Steps of a quarter of an hour at 4.4 kW from the first hour up to the last, 14 A (3.22 kW) for the golden system. */
    private static List<ForecastStep> quarters(LocalDate day, int fromHour, int toHour) {
        var steps = new ArrayList<ForecastStep>();
        for (var start = at(day, fromHour); start.isBefore(at(day, toHour)); start = start.plus(Duration.ofMinutes(15))) {
            steps.add(new ForecastStep(start, start.plus(Duration.ofMinutes(15)), 4.4));
        }
        return steps;
    }

    @Test
    void goldenQuarterHourStepsPlannedAtTwentyPast() {
        var day = plan(quarters(SUMMER_FRI, 10, 16), Forecasts.golden(), 62, at(SUMMER_FRI, 10, 20)).getFirst();
        // 14 kWh at 3.22 kW is 4 h 20.9 min from 10:20, rounded up to 14:45
        assertThat(spans(day)).containsExactly(new Span(at(SUMMER_FRI, 10, 20), at(SUMMER_FRI, 14, 45), 14));
        var window = day.windows().getFirst();
        assertThat(window.powerKw()).isEqualTo(3.22);
        assertThat(window.solarKwh()).isCloseTo(3.22 * 265 / 60, CLOSE);
        assertThat(window.importKwh()).isZero();
        assertThat(day.levelAfterChargingPct().value()).isCloseTo(80, CLOSE);
        // 13 h 40 min of the day are left
        assertThat(day.drivingPct()).isCloseTo(18.0 * 820 / 1440, CLOSE);
        assertThat(day.endLevelPct().value()).isCloseTo(80 - 18.0 * 820 / 1440, CLOSE);
    }

    @Test
    void ofTwoRunsWithTheSameSunTheEarlierWins() {
        // 2 kWh: 37 min at 14 A, so the 45-minute minimum; either hour could give it
        var forecast = hourly(SUMMER_FRI, Map.of(9, 4.4, 13, 4.4));
        var day = plan(forecast, Forecasts.golden(), 80 - 2 * 0.9 / 70 * 100, at(SUMMER_FRI, 0)).getFirst();
        assertThat(spans(day)).containsExactly(new Span(at(SUMMER_FRI, 9), at(SUMMER_FRI, 9, 45), 14));
        assertThat(day.windows().getFirst().solarKwh()).isCloseTo(3.22 * 0.75, CLOSE);
    }

    @Test
    void aGapInTheForecastSplitsTheSunIntoTwoWindowsInTimeOrder() {
        var forecast = new ArrayList<>(hourly(SUMMER_FRI, Map.of(9, 4.4, 10, 4.4, 13, 4.4, 14, 4.4)));
        // the forecast has nothing from 11:00 to 13:00
        forecast.removeIf(step -> !step.start().isBefore(at(SUMMER_FRI, 11)) && step.start().isBefore(at(SUMMER_FRI, 13)));
        var day = plan(forecast, Forecasts.golden(), 40, at(SUMMER_FRI, 0)).getFirst();
        assertThat(spans(day)).containsExactly(
                new Span(at(SUMMER_FRI, 9), at(SUMMER_FRI, 11), 14), new Span(at(SUMMER_FRI, 13), at(SUMMER_FRI, 15), 14));
        assertThat(day.windows()).extracting(Window::solarKwh).allSatisfy(kwh -> assertThat(kwh).isCloseTo(6.44, CLOSE));
    }

    @Test
    void aRunningAnnouncedWindowCutsTheNeedAndKeepsTheSunBesideIt() {
        // 10:00 to 14:00 at 14 A was announced; at 12:00 half of its 12.88 kWh is still to come
        var announced = new Window(at(SUMMER_FRI, 10), at(SUMMER_FRI, 14), 14, 3.22, 12.88, 0);
        var forecast = hourly(SUMMER_FRI, Map.of(10, 4.4, 11, 4.4, 12, 4.4, 13, 4.4, 14, 4.4, 15, 4.4, 17, 4.4));
        // need 8.05 kWh, 6.44 of it from the announced window: 1.61 kWh is 30 min at 14 A, so the 45-minute minimum.
        // The sun from 14:00 to 16:00 touches the announced window, so it is not a window of its own.
        var day = new Planner(Forecasts.golden(), PREFERENCES, WEEKLY_AWAY)
                .plan(forecast, new Percent(69.65), at(SUMMER_FRI, 12), List.of(announced)).getFirst();
        assertThat(spans(day)).containsExactly(
                new Span(at(SUMMER_FRI, 10), at(SUMMER_FRI, 14), 14), new Span(at(SUMMER_FRI, 17), at(SUMMER_FRI, 17, 45), 14));
        assertThat(day.windows()).extracting(Window::solarKwh)
                .satisfiesExactly(kwh -> assertThat(kwh).isCloseTo(6.44, CLOSE), kwh -> assertThat(kwh).isCloseTo(2.415, CLOSE));
        assertThat(day.levelAfterChargingPct().value()).isCloseTo(80, CLOSE);
    }

    @Test
    void aRunningAnnouncedWindowBelongsOnlyToTheDayItStarts() {
        var announced = new Window(at(SUMMER_FRI, 10), at(SUMMER_FRI, 14), 14, 3.22, 12.88, 0);
        var forecast = new ArrayList<>(hourly(SUMMER_FRI, Map.of(10, 4.4, 11, 4.4, 12, 4.4, 13, 4.4)));
        forecast.addAll(hourly(SUMMER_FRI.plusDays(1), Map.of()));
        var days = new Planner(Forecasts.golden(), PREFERENCES, WEEKLY_AWAY)
                .plan(forecast, new Percent(40), at(SUMMER_FRI, 12), List.of(announced));
        assertThat(spans(days.get(0))).containsExactly(new Span(at(SUMMER_FRI, 10), at(SUMMER_FRI, 14), 14));
        assertThat(days.get(1).windows()).isEmpty();
    }

    @Test
    void aSurplusForTheMinimumCurrentOnlyGivesAWindowAtThatCurrent() {
        // 2.3 kW leaves 1.445 kW: 6 A (1.38 kW); 7 A would import 0.165 kWh an hour
        var day = plan(hourly(SUMMER_FRI, Map.of(9, 2.3, 10, 2.3, 11, 2.3)), Forecasts.golden(), 40, at(SUMMER_FRI, 0))
                .getFirst();
        assertThat(spans(day)).containsExactly(new Span(at(SUMMER_FRI, 9), at(SUMMER_FRI, 12), 6));
        assertThat(day.windows().getFirst().solarKwh()).isCloseTo(1.38 * 3, CLOSE);
        assertThat(day.windows().getFirst().importKwh()).isZero();
    }

    @Test
    void aRunOfExactlyTheMinimumLengthTakesTheCurrentThatImportsWithinTheTolerance() {
        // 45 minutes of quarter-hour steps leaving 3.23 kW: 15 A (3.45 kW) imports 0.165 kWh, 16 A would import 0.34
        var day = plan(quarters(SUMMER_FRI, 10, 11).subList(0, 3), Forecasts.golden(), 40, at(SUMMER_FRI, 0)).getFirst();
        assertThat(spans(day)).containsExactly(new Span(at(SUMMER_FRI, 10), at(SUMMER_FRI, 10, 45), 15));
        var window = day.windows().getFirst();
        assertThat(window.powerKw()).isCloseTo(3.45, CLOSE);
        assertThat(window.solarKwh()).isCloseTo(3.23 * 0.75, CLOSE);
        assertThat(window.importKwh()).isCloseTo(0.22 * 0.75, CLOSE);
    }

    @Test
    void aWindowOverStepsWithLessSunIsCutWhereTheEnergyIsReached() {
        // with room to import, 11 A (2.53 kW) is the lowest current that gives 4.025 kWh over both hours: 2.53 kWh in
        // the first, then the 1.495 kWh left at the 1.61 kW of the second hour takes 55.7 min, rounded up to 12:00
        var tolerant = new PlanningPreferences(new Percent(30), new Percent(80), 0.85, 5,
                Duration.ofMinutes(45), Duration.ofMinutes(15));
        var finder = new WindowFinder(Forecasts.golden(), tolerant);
        var run = new SurplusRun(List.of(new Segment(at(SUMMER_FRI, 10), at(SUMMER_FRI, 11), 3.22),
                new Segment(at(SUMMER_FRI, 11), at(SUMMER_FRI, 12), 1.61)));
        var window = finder.windows(List.of(run), 4.025).getFirst();
        assertThat(new Span(window.start(), window.end(), window.amps()))
                .isEqualTo(new Span(at(SUMMER_FRI, 10), at(SUMMER_FRI, 12), 11));
        assertThat(window.solarKwh()).isCloseTo(2.53 + 1.61, CLOSE);
        assertThat(window.importKwh()).isCloseTo(2.53 - 1.61, CLOSE);
    }

    @Test
    void theFinderGivesItsWindowsInTimeOrderAlthoughItPicksTheSunniestFirst() {
        var finder = new WindowFinder(Forecasts.golden(), PREFERENCES);
        var runs = finder.runs(hourly(SUMMER_FRI, Map.of(9, 4.4, 13, 4.4, 14, 4.4)), at(SUMMER_FRI, 0), List.of());
        assertThat(finder.windows(runs, 100)).extracting(Window::start).containsExactly(at(SUMMER_FRI, 9), at(SUMMER_FRI, 13));
    }

    @Test
    void withoutAForecastTheAwayTimeIsTheOneOffPeriods() {
        var oneOff = new OneOffAwayPeriod(at(SUMMER_FRI, 8), at(SUMMER_FRI, 12));
        assertThat(new AwayTimes(WEEKLY_AWAY.weekly(), List.of(oneOff), List.of()).during(List.of(), Forecasts.TZ))
                .containsExactly(new BlockedTime(oneOff.start(), oneOff.end()));
    }

    @Test
    void aWindowIsCutByTheLengthOfItsStepsNotByTheirCount() {
        // a charger that only does 14 A (3.22 kW): half an hour at 3.22 kW, then 1.61 kW, so 2.415 kWh by 11:00
        var system = Forecasts.golden().withCharger(new Charger(Phases.ONE, 14, 14, 230, 0.90));
        var tolerant = new PlanningPreferences(new Percent(30), new Percent(80), 0.85, 5,
                Duration.ofMinutes(45), Duration.ofMinutes(15));
        var run = new SurplusRun(List.of(new Segment(at(SUMMER_FRI, 10), at(SUMMER_FRI, 10, 30), 3.22),
                new Segment(at(SUMMER_FRI, 10, 30), at(SUMMER_FRI, 12), 1.61)));
        var window = new WindowFinder(system, tolerant).windows(List.of(run), 2.415).getFirst();
        assertThat(new Span(window.start(), window.end(), window.amps()))
                .isEqualTo(new Span(at(SUMMER_FRI, 10), at(SUMMER_FRI, 11), 14));
        assertThat(window.solarKwh()).isCloseTo(2.415, CLOSE);
        assertThat(window.importKwh()).isCloseTo(0.805, CLOSE);
    }
}
