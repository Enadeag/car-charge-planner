package io.github.enadeag.carchargeplanner.engine.plan;

import static io.github.enadeag.carchargeplanner.engine.plan.Forecasts.PREFERENCES;
import static io.github.enadeag.carchargeplanner.engine.plan.Forecasts.at;
import static io.github.enadeag.carchargeplanner.engine.plan.Forecasts.bellDay;
import static io.github.enadeag.carchargeplanner.engine.plan.Forecasts.bellDays;
import static io.github.enadeag.carchargeplanner.engine.plan.Forecasts.hourly;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.github.enadeag.carchargeplanner.engine.config.HomeSystem;
import io.github.enadeag.carchargeplanner.engine.config.PlanningPreferences;
import io.github.enadeag.carchargeplanner.engine.forecast.ForecastStep;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
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
        return new Planner(system, PREFERENCES).plan(forecast, soc, now);
    }

    /** A bell-shaped Friday for the example system, planned from midnight or the given hour. */
    private static DayPlan friday(double peakWm2, double soc, int nowHour) {
        return plan(bellDay(SUMMER_FRI, peakWm2), Forecasts.example(), soc, at(SUMMER_FRI, nowHour)).getFirst();
    }

    private static DayPlan friday(double peakWm2, double soc) {
        return friday(peakWm2, soc, 0);
    }

    @Test
    void clearSummerDayGivesOneLongWindowNearTheSurplusPeak() {
        var day = friday(900, 40);
        assertThat(day.windows()).hasSize(1);
        var window = day.windows().getFirst();
        assertThat(window.amps()).isBetween(6, 32);
        assertThat(window.powerKw()).isBetween(2.0, 3.2); // peak usable is about 2.6 kW
        assertThat(window.importKwh()).isLessThanOrEqualTo(PREFERENCES.importToleranceKwh());
        assertThat(window.start()).isAfterOrEqualTo(at(SUMMER_FRI, 8));
        assertThat(window.end()).isBeforeOrEqualTo(at(SUMMER_FRI, 17));
        assertThat(window.solarShare()).isGreaterThan(0.95);
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
        assertThat(day.socAfterChargingPct()).isCloseTo(80, CLOSE);
        assertThat(day.drivingPct()).isCloseTo(18, CLOSE);
        assertThat(day.socEndOfDayPct()).isCloseTo(62, CLOSE);
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
                .flatMap(d -> java.util.stream.Stream.of(d.socStartPct(), d.socAfterChargingPct(), d.socEndOfDayPct()))
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
        assertThat(day.socAfterChargingPct()).isCloseTo(80, CLOSE);
    }

    @Test
    void overcastDayHasNoWindow() {
        var day = friday(150, 40);
        assertThat(day.windows()).isEmpty();
        assertThat(day.socAfterChargingPct()).isEqualTo(40);
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
        double usable = Charging.usableKw(step, system, PREFERENCES);
        assertThat(Charging.ampsFor(usable, system.charger())).isEqualTo((int) (usable * 1000 / 230));
        assertThat(Charging.ampsFor(50, system.charger())).isEqualTo(32);
    }

    @Test
    void windowIsTrimmedToTheEnergyNeeded() {
        var day = friday(900, 75);
        double need = new Planner(Forecasts.example(), PREFERENCES).energyNeededKwh(75);
        double delivered = day.windows().stream().mapToDouble(Window::solarKwh).sum();
        assertThat(delivered).isGreaterThanOrEqualTo(need).isLessThan(need + 1.5);
        var full = friday(900, 40).windows().getFirst();
        var trimmed = day.windows().getFirst();
        assertThat(Duration.between(trimmed.start(), trimmed.end())).isLessThan(Duration.between(full.start(), full.end()));
    }

    @Test
    void shortNeedStillGetsTheMinimumWindowLength() {
        var window = friday(900, 79.8).windows().getFirst();
        assertThat(Duration.between(window.start(), window.end()))
                .isGreaterThanOrEqualTo(Duration.ofMinutes(PREFERENCES.minWindowMinutes()));
    }

    @Test
    void noWindowWhenTheBatteryIsAtOrAboveTheTarget() {
        var day = friday(900, 85);
        assertThat(day.windows()).isEmpty();
        assertThat(day.socAfterChargingPct()).isEqualTo(85);
    }

    @Test
    void aHigherMarginGivesMorePower() {
        var cautious = friday(900, 40).windows().getFirst();
        var bold = new Planner(Forecasts.example(), new PlanningPreferences(30, 80, 1.0, 0.2, 45, 15))
                .plan(bellDay(SUMMER_FRI, 900), 40, at(SUMMER_FRI, 0)).getFirst().windows().getFirst();
        assertThat(bold.powerKw()).isGreaterThan(cautious.powerKw());
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
        assertThat(day.socAfterChargingPct()).isCloseTo(80, CLOSE);
    }

    @Test
    void springForwardDayPlannedFromMidnightTakesAWholeDaysDriving() {
        var day = plan(hourly(SPRING_FORWARD, Map.of()), Forecasts.golden(), 60, at(SPRING_FORWARD, 0)).getFirst();
        assertThat(day.drivingPct()).isCloseTo(18, CLOSE);
        assertThat(day.socEndOfDayPct()).isCloseTo(42, CLOSE);
    }

    @Test
    void fallBackDayPlansTheAfternoonByLocalTimeAndOneDaysDriving() {
        // keys count real hours, so after the repeated 02:00 hour key 11 is 10:00 local time
        var forecast = hourly(FALL_BACK, Map.of(10, 2.6, 11, 4.4, 12, 4.4, 13, 4.4, 14, 4.4, 15, 4.4, 16, 2.6));
        var day = plan(forecast, Forecasts.golden(), 62, at(FALL_BACK, 0)).getFirst();
        assertThat(spans(day)).containsExactly(new Span(at(FALL_BACK, 10), at(FALL_BACK, 14, 25), 14));
        assertThat(day.windows().getFirst().solarKwh()).isCloseTo(3.22 * 53 / 12, CLOSE);
        assertThat(day.socAfterChargingPct()).isCloseTo(80, CLOSE);
        assertThat(day.drivingPct()).isCloseTo(18, CLOSE);
        assertThat(day.socEndOfDayPct()).isCloseTo(62, CLOSE);
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
        assertThat(day.socAfterChargingPct()).isCloseTo(80, CLOSE);
    }

    @Test
    void runAcrossMidnightIsOneWindowThatBelongsToTheDayItStarts() {
        var start = at(SUMMER_FRI, 22);
        var forecast = IntStream.range(0, 6)
                .mapToObj(h -> new ForecastStep(start.plus(Duration.ofHours(h)), start.plus(Duration.ofHours(h + 1)), 3.3))
                .toList();
        var finder = new WindowFinder(Forecasts.example(), PREFERENCES);
        var runs = finder.runs(forecast, start);
        assertThat(runs).hasSize(1);
        assertThat(WindowFinder.byDay(runs, Forecasts.TZ)).containsOnlyKeys(SUMMER_FRI);
        var window = finder.windows(runs, 100).getFirst();
        assertThat(window.start()).isEqualTo(start);
        assertThat(window.end()).isEqualTo(start.plus(Duration.ofHours(6)));
    }

    @Test
    void nowCutsOffTheFirstStep() {
        assertThat(friday(900, 40, 12).windows().getFirst().start()).isAfterOrEqualTo(at(SUMMER_FRI, 12));
    }

    @Test
    void theDrivingOfADayIsTakenOffBeforeTheNext() {
        var friday = SUMMER_FRI;
        var saturday = SUMMER_FRI.plusDays(1);
        var days = plan(bellDays(Map.of(friday, 150.0, saturday, 150.0)), Forecasts.example(), 60, at(friday, 0));
        double driving = 600.0 / 7 * 18 / 100 / 70 * 100;
        assertThat(days.get(0).socEndOfDayPct()).isCloseTo(60 - driving, CLOSE);
        assertThat(days.get(1).socStartPct()).isCloseTo(days.get(0).socEndOfDayPct(), within(1e-9));
    }

    @Test
    void aSunnyDayAfterADullDayChargesFromTheLowerLevel() {
        var saturday = SUMMER_FRI.plusDays(1);
        var days = plan(bellDays(Map.of(SUMMER_FRI, 150.0, saturday, 900.0)), Forecasts.example(), 60, at(SUMMER_FRI, 0));
        assertThat(days.get(0).windows()).isEmpty();
        assertThat(days.get(1).windows()).isNotEmpty();
        assertThat(days.get(1).socStartPct()).isLessThan(60);
    }

    @Test
    void aSurplusThatOnePhaseCanUseIsTooSmallForThree() {
        // about 3 kW of surplus: 13 A on one phase, but under the 4.1 kW that 6 A on three phases already takes
        var forecast = bellDay(SUMMER_FRI, 1000);
        assertThat(plan(forecast, Forecasts.exampleOnPhases(1), 40, at(SUMMER_FRI, 0)).getFirst().windows()).isNotEmpty();
        assertThat(plan(forecast, Forecasts.exampleOnPhases(3), 40, at(SUMMER_FRI, 0)).getFirst().windows()).isEmpty();
    }

    @Test
    void aThreePhasePlanNeverGoesBelowTheThreePhaseMinimum() {
        var system = Forecasts.exampleOnPhases(3);
        var charger = system.charger();
        // 2500 W/m2 at the peak is an artificial sky: about 10 kW, enough for three phases
        var windows = plan(bellDay(SUMMER_FRI, 2500), system, 40, at(SUMMER_FRI, 0)).getFirst().windows();
        assertThat(windows).isNotEmpty().allSatisfy(window -> {
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
}
