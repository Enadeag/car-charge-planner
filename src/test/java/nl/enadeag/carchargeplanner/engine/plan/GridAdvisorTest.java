package nl.enadeag.carchargeplanner.engine.plan;

import static nl.enadeag.carchargeplanner.engine.plan.Forecasts.NIGHT_CHARGING;
import static nl.enadeag.carchargeplanner.engine.plan.Forecasts.PREFERENCES;
import static nl.enadeag.carchargeplanner.engine.plan.Forecasts.TZ;
import static nl.enadeag.carchargeplanner.engine.plan.Forecasts.WEEKLY_AWAY;
import static nl.enadeag.carchargeplanner.engine.plan.Forecasts.at;
import static nl.enadeag.carchargeplanner.engine.plan.Forecasts.bellDays;
import static nl.enadeag.carchargeplanner.engine.plan.Forecasts.hourly;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assertions.within;

import nl.enadeag.carchargeplanner.engine.away.OneOffAwayPeriod;
import nl.enadeag.carchargeplanner.engine.config.HomeSystem;
import nl.enadeag.carchargeplanner.engine.config.Percent;
import nl.enadeag.carchargeplanner.engine.config.Phases;
import nl.enadeag.carchargeplanner.engine.config.PlanningPreferences;
import nl.enadeag.carchargeplanner.engine.forecast.ForecastStep;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
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

    private static Advised advise(HomeSystem system, PlanningPreferences preferences, List<ForecastStep> forecast,
            double soc, Instant now, AwayTimes away) {
        var days = new Planner(system, preferences, away).plan(forecast, new Percent(soc), now, List.of());
        var advice = new GridAdvisor(system, preferences, away).advise(days, forecast, now).orElse(null);
        return new Advised(days, advice);
    }

    private static Advised advise(HomeSystem system, List<ForecastStep> forecast, double soc, Instant now,
            AwayTimes away) {
        return advise(system, PREFERENCES, forecast, soc, now, away);
    }

    /** With charging hours from 20:00 to 06:00, so a grid charge may start in the night. */
    private static Advised adviseAtNight(HomeSystem system, List<ForecastStep> forecast, double soc, Instant now,
            AwayTimes away) {
        return advise(system, NIGHT_CHARGING, forecast, soc, now, away);
    }

    private static Advised advise(HomeSystem system, List<ForecastStep> forecast, double soc, Instant now) {
        return advise(system, forecast, soc, now, WEEKLY_AWAY);
    }

    /** The example's weekly away period, and this one entered by hand. */
    private static Advised adviseAway(List<ForecastStep> forecast, double soc, Instant now, OneOffAwayPeriod oneOff) {
        var away = new AwayTimes(WEEKLY_AWAY.weekly(), List.of(oneOff), List.of());
        return advise(Forecasts.example(), forecast, soc, now, away);
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
        var advised = advise(days(120, 120, 900), 55, fridayAt(7));
        // today loses 17 of its 24 hours of driving, tomorrow a whole day's
        double today = 55 - DAILY_DRIVING_PCT * 17 / 24;
        double tomorrow = today - DAILY_DRIVING_PCT;
        assertThat(advised.days().getFirst().endLevelPct().value()).isCloseTo(today, CLOSE);
        assertThat(advised.advice().day()).isEqualTo(SAT);
        assertThat(advised.advice().endLevelPct()).isCloseTo(tomorrow, CLOSE);
        assertThat(advised.advice().deficitPct()).isCloseTo(30 - tomorrow, CLOSE);
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
        assertThat(advice.endLevelPct()).isCloseTo(5 - DAILY_DRIVING_PCT, CLOSE);
        assertThat(advice.deficitPct()).isCloseTo(30 - (5 - DAILY_DRIVING_PCT), CLOSE);
    }

    @Test
    void withoutSunTheLowestCurrentFromTheStartOfTheChargingHours() {
        var advised = advise(days(0, 0), 35, fridayAt(0));
        var window = advised.window();
        // 13.25 kWh at 1.38 kW: 9 h 36 min from 07:00, rounded up to 16:40
        assertThat(window.amps()).isEqualTo(6);
        assertThat(window.start()).isEqualTo(fridayAt(7));
        assertThat(window.end()).isEqualTo(at(FRI, 16, 40));
        assertThat(window.solarKwh()).isZero();
        assertThat(window.importKwh()).isCloseTo(advised.advice().energyKwh(), CLOSE);
    }

    @Test
    void thePlacementPrefersTheHoursWithLeftoverSun() {
        // the 7 A solar window from 10:00 to 14:00 leaves some afternoon sun; 16.95 kWh needs 9 h 13 min at 8 A,
        // and no lower current fits a stretch the car is free, so it starts as the solar window ends
        var window = advise(days(650, 650), 22, fridayAt(0)).window();
        assertThat(window.start()).isEqualTo(fridayAt(14));
        assertThat(window.end()).isEqualTo(at(FRI, 23, 15));
        assertThat(window.amps()).isEqualTo(8);
        assertThat(window.solarKwh()).isCloseTo(2.540625, within(1e-6));
        assertThat(window.importKwh()).isCloseTo(14.410234, within(1e-6));
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
        assertThat(advised.days().getFirst().windows()).extracting(Window::start, Window::end, Window::amps)
                .containsExactly(tuple(fridayAt(9), fridayAt(15), 9));
        var window = advised.window();
        assertThat(List.of(window.start(), window.end())).containsExactly(fridayAt(15), at(FRI, 23, 45));
        for (var day : advised.days()) {
            for (var solar : day.windows()) {
                assertThat(!window.end().isAfter(solar.start()) || !window.start().isBefore(solar.end()))
                        .as("%s overlaps %s", window, solar).isTrue();
            }
        }
    }

    @Test
    void theWindowNeverOverlapsAnAwayPeriod() {
        var away = OneOffAwayPeriod.on(FRI, LocalTime.of(0, 0), LocalTime.of(20, 0), TZ);
        var window = adviseAway(dull(2), 30, fridayAt(0), away).window();
        assertThat(!window.end().isAfter(away.start()) || !window.start().isBefore(away.end()))
                .as("%s overlaps %s", window, away).isTrue();
    }

    @Test
    void theWindowFallsBackToAnEarlierDayWhenTheDipDayHasNoTimeAtHome() {
        var away = OneOffAwayPeriod.allDay(SAT, TZ);
        // Friday ends at 33 %, Saturday at 10.9 %: 14.84 kWh, which the car can take only on Friday, from 07:00
        var advice = adviseAway(dull(3), 55, fridayAt(0), away).advice();
        assertThat(advice.day()).isEqualTo(SAT);
        var window = advice.window().orElseThrow();
        assertThat(List.of(window.start(), window.end())).containsExactly(fridayAt(7), at(FRI, 17, 50));
        assertThat(window.amps()).isEqualTo(6);
    }

    @Test
    void noWindowWhenTheCarIsAwayEveryDay() {
        var away = new OneOffAwayPeriod(fridayAt(0), at(LocalDate.of(2026, 7, 10), 0));
        var advice = adviseAway(dull(2), 20, fridayAt(0), away).advice();
        assertThat(advice.day()).isEqualTo(FRI);
        assertThat(advice.energyKwh()).isCloseTo((30 - (20 - DAILY_DRIVING_PCT)) / 100 * 70 / 0.9, CLOSE);
        assertThat(advice.window()).isEmpty();
    }

    @Test
    void lateInTheEveningTodayOnlyLosesTheRestOfTheDaysDriving() {
        var advised = advise(dull(1), 33, fridayAt(22));
        assertThat(advised.advice()).isNull(); // 2 hours of the day left: about 1.8 % of the battery, still above 30 %
        assertThat(advised.days().getFirst().drivingPct()).isCloseTo(DAILY_DRIVING_PCT * 2 / 24, CLOSE);
    }

    @Test
    void adviceAcrossTheSpringForwardSwitchLastsTheRealHoursNeeded() {
        var saturday = LocalDate.of(2026, 3, 28);
        var sunday = LocalDate.of(2026, 3, 29); // 23 hours
        var forecast = new ArrayList<>(hourly(saturday, Map.of()));
        forecast.addAll(hourly(sunday, Map.of()));
        var advice = adviseAtNight(Forecasts.golden(), forecast, 57, at(saturday, 0), WEEKLY_AWAY).advice();
        // 57 % - 18 % - 18 % = 21 % on Sunday: 9 % of 70 kWh / 0.9 = 7 kWh
        assertThat(advice.day()).isEqualTo(sunday);
        assertThat(advice.endLevelPct()).isCloseTo(21, CLOSE);
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
        assertThat(advice.endLevelPct()).isCloseTo(28.28, CLOSE);
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
        // after the solar window's 8.28 %: 38 % + 8.28 % + 1.72 %
        assertThat(advice.chargeLimitPct()).contains(new Percent(48));
    }

    @Test
    void theAdviceIsForTheFirstDipOnly() {
        var advised = advise(dull(4), 40, fridayAt(7));
        var firstDip = advised.days().stream()
                .filter(day -> day.levelAfterChargingPct().value() - day.drivingPct() < PREFERENCES.minimumPct().value() - 1)
                .findFirst().orElseThrow();
        assertThat(advised.advice().day()).isEqualTo(firstDip.date());
    }

    @Test
    void aTinyShortfallIsNotWorthAWarning() {
        // the first day ends about 0.5 points under the minimum
        double soc = PREFERENCES.minimumPct().value() - 0.5 + DAILY_DRIVING_PCT;
        var advised = advise(dull(1), soc, fridayAt(0));
        assertThat(PREFERENCES.minimumPct().value() - advised.days().getFirst().endLevelPct().value()).isCloseTo(0.5, CLOSE);
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
        var window = adviseAtNight(Forecasts.golden(), hourly(day, Map.of()), soc, at(day, 0), WEEKLY_AWAY).window();
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
        // 8.25 kWh at 6 A: 6 hours on one phase (1.38 kW), 2 on three (4.14 kW)
        assertThat(window.amps()).isEqualTo(charger.minAmps());
        assertThat(window.powerKw()).isCloseTo(1.38 * phases.count(), CLOSE);
        assertThat(window.start()).isEqualTo(fridayAt(7));
        assertThat(window.end()).isEqualTo(fridayAt(phases == Phases.ONE ? 13 : 9));
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
        assertThat(advice.energyKwh()).isCloseTo((30 - (5 - DAILY_DRIVING_PCT * 2 / 24)) / 100 * 70 / 0.9, CLOSE);
        assertThat(advice.energyKwh()).isGreaterThan(Charging.powerKw(32, Forecasts.example().charger()) * 2);
        assertThat(advice.window()).isEmpty();
    }

    @Test
    void aDayThatEndsExactlyOnePointBelowTheMinimumGivesNoAdvice() {
        // 47 % - 18 % = 29 %: just not more than the one point of noise under 30 %
        var advised = advise(Forecasts.golden(), hourly(FRI, Map.of()), 47, fridayAt(0));
        assertThat(advised.days().getFirst().endLevelPct().value()).isEqualTo(29.0);
        assertThat(advised.advice()).isNull();
    }

    @Test
    void theChargersMaximumCurrentIsTriedWhenNothingLessFits() {
        // 14.5 kWh in the two hours left: 32 A (7.36 kW) takes 1.97 hours, 31 A (7.13 kW) 2.03
        double soc = 30 - 14.5 * 0.9 / 70 * 100 + 18.0 * 2 / 24;
        var advice = advise(Forecasts.golden(), hourly(FRI, Map.of()), soc, fridayAt(22)).advice();
        assertThat(advice.energyKwh()).isCloseTo(14.5, CLOSE);
        var window = advice.window().orElseThrow();
        assertThat(window.amps()).isEqualTo(32);
        assertThat(window.start()).isEqualTo(fridayAt(22));
        assertThat(window.end()).isEqualTo(at(SAT, 0));
    }

    @Test
    void anEnergyOfWholeQuartersFitsTheLastFreeHourOfTheDay() {
        // 1.38 kWh: exactly one hour at 6 A, and the car is home only from 23:00
        var away = new AwayTimes(List.of(), List.of(new OneOffAwayPeriod(fridayAt(0), fridayAt(23))), List.of());
        double soc = 48 - 1.38 * 0.9 / 70 * 100;
        var advice = adviseAtNight(Forecasts.golden(), hourly(FRI, Map.of()), soc, fridayAt(0), away).advice();
        assertThat(advice.energyKwh()).isCloseTo(1.38, CLOSE);
        var window = advice.window().orElseThrow();
        assertThat(window.amps()).isEqualTo(6);
        assertThat(window.start()).isEqualTo(fridayAt(23));
        assertThat(window.end()).isEqualTo(at(SAT, 0));
        assertThat(window.importKwh()).isCloseTo(1.38, CLOSE);
    }
}
