package io.github.enadeag.carchargeplanner.engine.plan;

import static io.github.enadeag.carchargeplanner.engine.plan.Forecasts.PREFERENCES;
import static io.github.enadeag.carchargeplanner.engine.plan.Forecasts.TZ;
import static io.github.enadeag.carchargeplanner.engine.plan.Forecasts.at;
import static io.github.enadeag.carchargeplanner.engine.plan.Forecasts.bellDays;
import static io.github.enadeag.carchargeplanner.engine.plan.Forecasts.hourly;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assertions.within;

import io.github.enadeag.carchargeplanner.engine.config.HomeSystem;
import io.github.enadeag.carchargeplanner.engine.config.Percent;
import io.github.enadeag.carchargeplanner.engine.config.Phases;
import io.github.enadeag.carchargeplanner.engine.forecast.ForecastStep;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class GridAdvisorTest {

    private static final LocalDate FRI = LocalDate.of(2026, 7, 3);
    private static final LocalDate SAT = FRI.plusDays(1);
    private static final Offset<Double> CLOSE = within(1e-6);
    /** The example's 600 km a week, as a share of its 70 kWh battery. */
    private static final double DAILY_DRIVING_PCT = 600.0 / 7 * 18 / 100 / 70 * 100;

    private record Advised(List<DayPlan> days, GridAdvice advice) {

        Window window() {
            return advice.window().orElseThrow();
        }
    }

    private static Advised advise(HomeSystem system, List<ForecastStep> forecast, double soc, Instant now) {
        var days = new Planner(system, PREFERENCES).plan(forecast, new Percent(soc), now, List.of());
        var advice = new GridAdvisor(system, PREFERENCES).advise(days, forecast, now).orElse(null);
        return new Advised(days, advice);
    }

    private static Advised advise(List<ForecastStep> forecast, double soc, Instant now) {
        return advise(Forecasts.example(), forecast, soc, now);
    }

    /** Bell-shaped days from Friday, given by their peak irradiance (W/m2). */
    private static List<ForecastStep> days(double... peaksWm2) {
        var peaks = new LinkedHashMap<LocalDate, Double>();
        for (int i = 0; i < peaksWm2.length; i++) {
            peaks.put(FRI.plusDays(i), peaksWm2[i]);
        }
        return bellDays(peaks);
    }

    /** Dull days from Friday: too little sun for a charge window. */
    private static List<ForecastStep> dull(int count) {
        var peaks = new double[count];
        Arrays.fill(peaks, 120);
        return days(peaks);
    }

    private static Instant fridayAt(int hour) {
        return at(FRI, hour);
    }

    @Test
    void noAdviceWhenTheSunKeepsTheCarAboveTheMinimum() {
        assertThat(advise(days(900, 900), 60, fridayAt(7)).advice()).isNull();
    }

    @Test
    void noAdviceOnADullDayWithAComfortableBattery() {
        assertThat(advise(dull(1), 70, fridayAt(7)).advice()).isNull();
    }

    @Test
    void aDipBeforeTheNextSunnyDayGivesAdvice() {
        var advised = advise(days(120, 120, 900), 55, fridayAt(7)); // ends today about 39 %, tomorrow about 17 %
        assertThat(advised.advice().day()).isEqualTo(SAT);
        assertThat(advised.days().getFirst().socEndOfDayPct().value()).isGreaterThan(PREFERENCES.minimumPct().value());
        assertThat(advised.advice().deficitPct()).isPositive();
    }

    @Test
    void theAdvisedEnergyMatchesTheDeficit() {
        var advice = advise(dull(3), 35, fridayAt(7)).advice();
        assertThat(advice.energyKwh()).isCloseTo(advice.deficitPct() / 100 * 70 / 0.9, within(1e-9));
        var window = advice.window().orElseThrow();
        assertThat(window.solarKwh() + window.importKwh()).isCloseTo(advice.energyKwh(), CLOSE);
    }

    @Test
    void theTrueShortfallIsKeptWhenTheDrivingNeedsMoreThanTheBattery() {
        var advice = advise(dull(1), 5, fridayAt(0)).advice();
        assertThat(advice.socEndPct()).isNegative();
        assertThat(advice.deficitPct()).isGreaterThan(PREFERENCES.minimumPct().value());
    }

    @Test
    void withoutSunTheLowestCurrentFromTheEarliestFreeTime() {
        var window = advise(days(0, 0), 35, fridayAt(0)).window();
        assertThat(window.amps()).isEqualTo(6);
        assertThat(window.importKwh()).isPositive();
        assertThat(window.solarKwh()).isZero();
        assertThat(window.start().atZone(TZ).toLocalTime().getHour()).isZero();
    }

    @Test
    void thePlacementPrefersTheHoursWithLeftoverSun() {
        // a peak surplus of about 1.2 kW is below the 6 A minimum, so no solar window, but it still offsets grid draw
        var window = advise(days(650, 650), 22, fridayAt(0)).window();
        assertThat(window.solarKwh()).isPositive();
        assertThat(window.start().atZone(TZ).getHour()).isBetween(8, 14);
    }

    @Test
    void theWindowsEnergyIsItsPowerTimesItsLength() {
        var advice = advise(dull(3), 35, fridayAt(7)).advice();
        var window = advice.window().orElseThrow();
        double hours = Hours.between(window.start(), window.end());
        // the end is rounded up to five minutes
        assertThat(window.powerKw() * hours).isCloseTo(advice.energyKwh(), within(window.powerKw() * 5 / 60 + 1e-6));
    }

    @Test
    void theWindowStaysClearOfThePlannedSolarWindows() {
        // a sunny day that cannot make up for a very low battery
        var advised = advise(days(900, 120), 8, fridayAt(0));
        assertThat(advised.days().getFirst().windows()).isNotEmpty();
        var window = advised.window();
        for (var day : advised.days()) {
            for (var solar : day.windows()) {
                assertThat(!window.end().isAfter(solar.start()) || !window.start().isBefore(solar.end()))
                        .as("%s overlaps %s", window, solar).isTrue();
            }
        }
    }

    @Test
    void lateInTheEveningTodayOnlyLosesTheRestOfTheDaysDriving() {
        var advised = advise(dull(1), 33, fridayAt(22));
        assertThat(advised.advice()).isNull(); // 2 hours of the day left: about 1.8 % of the battery, still above 30 %
        assertThat(advised.days().getFirst().drivingPct()).isLessThan(2);
    }

    @Test
    void adviceAcrossTheSpringForwardSwitchLastsTheRealHoursNeeded() {
        var saturday = LocalDate.of(2026, 3, 28);
        var sunday = LocalDate.of(2026, 3, 29); // 23 hours
        var forecast = new ArrayList<>(hourly(saturday, Map.of()));
        forecast.addAll(hourly(sunday, Map.of()));
        var advice = advise(Forecasts.golden(), forecast, 57, at(saturday, 0)).advice();
        // 57 % - 18 % - 18 % = 21 % on Sunday: 9 % of 70 kWh / 0.9 = 7 kWh
        assertThat(advice.day()).isEqualTo(sunday);
        assertThat(advice.socEndPct()).isCloseTo(21, CLOSE);
        assertThat(advice.deficitPct()).isCloseTo(9, CLOSE);
        assertThat(advice.energyKwh()).isCloseTo(7, CLOSE);
        // no sun: the lowest current from the earliest free slot; 7 / 1.38 = 5.07 real hours, so 06:05 summer time
        var window = advice.window().orElseThrow();
        assertThat(window.start()).isEqualTo(at(sunday, 0));
        assertThat(window.end()).isEqualTo(at(sunday, 6, 5));
        assertThat(window.amps()).isEqualTo(6);
        assertThat(window.powerKw()).isEqualTo(1.38);
        assertThat(window.solarKwh()).isZero();
        assertThat(window.importKwh()).isCloseTo(7, CLOSE);
        assertThat(Hours.between(window.start(), window.end())).isCloseTo(61.0 / 12, CLOSE);
    }

    @Test
    void goldenAdviceTopsUpADayTheSunCannotFill() {
        // 4.4 kW leaves 3.23 kW (a 14 A window); 1.6 kW leaves 0.85 kW, below the 6 A minimum but it offsets grid draw
        var forecast = hourly(FRI, Map.of(10, 4.4, 11, 4.4, 12, 1.6, 13, 1.6, 14, 1.6, 15, 1.6));
        var advised = advise(Forecasts.golden(), forecast, 38, fridayAt(0));
        assertThat(advised.days().getFirst().windows())
                .extracting(Window::start, Window::end, Window::amps)
                .containsExactly(tuple(fridayAt(10), fridayAt(12), 14));
        // 6.44 kWh * 0.9 / 70 kWh = 8.28 %: 38 + 8.28 - 18 = 28.28 %, 1.72 % short: 1.204 kWh / 0.9
        var advice = advised.advice();
        assertThat(advice.day()).isEqualTo(FRI);
        assertThat(advice.socEndPct()).isCloseTo(28.28, CLOSE);
        assertThat(advice.deficitPct()).isCloseTo(1.72, CLOSE);
        assertThat(advice.energyKwh()).isCloseTo(1.204 / 0.9, CLOSE);
        // 6 A right after the solar window, in the leftover sun: 1.3378 / 1.38 h = 58.2 min, rounded up to 13:00
        var window = advice.window().orElseThrow();
        assertThat(window.start()).isEqualTo(fridayAt(12));
        assertThat(window.end()).isEqualTo(fridayAt(13));
        assertThat(window.amps()).isEqualTo(6);
        assertThat(window.powerKw()).isEqualTo(1.38);
        double hours = 1.204 / 0.9 / 1.38;
        assertThat(window.solarKwh()).isCloseTo(0.85 * hours, CLOSE);
        assertThat(window.importKwh()).isCloseTo(0.53 * hours, CLOSE);
    }

    @Test
    void theAdviceIsForTheFirstDipOnly() {
        var advised = advise(dull(4), 40, fridayAt(7));
        var firstDip = advised.days().stream()
                .filter(day -> day.socAfterChargingPct().value() - day.drivingPct() < PREFERENCES.minimumPct().value() - 1)
                .findFirst().orElseThrow();
        assertThat(advised.advice().day()).isEqualTo(firstDip.date());
    }

    @Test
    void aTinyShortfallIsNotWorthAWarning() {
        // the first day ends about 0.5 points under the minimum
        double soc = PREFERENCES.minimumPct().value() - 0.5 + DAILY_DRIVING_PCT;
        var advised = advise(dull(1), soc, fridayAt(0));
        assertThat(PREFERENCES.minimumPct().value() - advised.days().getFirst().socEndOfDayPct().value()).isBetween(0.0, 1.0);
        assertThat(advised.advice()).isNull();
    }

    @Test
    void adviceOnThe25HourFallBackDay() {
        var day = LocalDate.of(2026, 10, 25);
        var advice = advise(bellDays(Map.of(day, 100.0, day.plusDays(1), 100.0)), 12, at(day, 0)).advice();
        var window = advice.window().orElseThrow();
        assertThat(window.end()).isAfter(window.start());
        double hours = Hours.between(window.start(), window.end());
        assertThat(window.powerKw() * hours).isCloseTo(advice.energyKwh(), within(window.powerKw() * 5 / 60 + 1e-6));
    }

    @Test
    void aGridChargeAcrossTheRepeatedHourEndsInWinterTime() {
        var day = LocalDate.of(2026, 10, 25);
        // 3 h 25 min at 6 A (1.38 kW) from 00:00 summer time (22:00 UTC) ends at 02:25 winter time (01:25 UTC)
        double soc = 30 + 18 - 1.38 * 41 / 12 * 0.9 / 70 * 100;
        var window = advise(Forecasts.golden(), hourly(day, Map.of()), soc, at(day, 0)).window();
        assertThat(window.start()).isEqualTo(Instant.parse("2026-10-24T22:00:00Z"));
        assertThat(window.end()).isEqualTo(Instant.parse("2026-10-25T01:25:00Z"));
        assertThat(window.amps()).isEqualTo(6);
    }

    @ParameterizedTest
    @EnumSource(Phases.class)
    void theGridChargeUsesThePowerOfTheChargersPhases(Phases phases) {
        var system = Forecasts.exampleOnPhases(phases);
        var charger = system.charger();
        var window = advise(system, dull(3), 35, fridayAt(7)).window();
        assertThat(window.powerKw()).isCloseTo(Charging.powerKw(window.amps(), charger), CLOSE);
        assertThat(window.powerKw()).isGreaterThanOrEqualTo(Charging.powerKw(charger.minAmps(), charger) - 1e-9);
    }

    @Test
    void theGridChargeGoesOnAnEarlierDayWhenTheDipDayHasNoRoom() {
        // the forecast ends at 01:00 on Saturday: one free hour, too short for the 14.8 kWh Saturday falls short
        var forecast = new ArrayList<>(dull(1));
        forecast.add(new ForecastStep(at(SAT, 0), at(SAT, 1), 0));
        var advice = advise(forecast, 55, fridayAt(0)).advice();
        assertThat(advice.day()).isEqualTo(SAT);
        assertThat(advice.window().orElseThrow().start().atZone(TZ).toLocalDate()).isEqualTo(FRI);
    }

    @Test
    void knownFault40AGridChargeCannotCrossMidnight() {
        // At 22:00 Friday ends 26.8 % short: 20.9 kWh, 2.8 hours at the full 32 A. Only two hours of Friday are left,
        // and Saturday is not tried because the dip is on Friday, so there is no window although the car is home.
        var advice = advise(dull(2), 5, fridayAt(22)).advice();
        assertThat(advice.day()).isEqualTo(FRI);
        assertThat(advice.energyKwh()).isGreaterThan(Charging.powerKw(32, Forecasts.example().charger()) * 2);
        assertThat(advice.window()).isEmpty();
    }
}
