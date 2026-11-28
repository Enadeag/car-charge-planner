package nl.enadeag.carchargeplanner.engine.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import nl.enadeag.carchargeplanner.engine.config.Car;
import nl.enadeag.carchargeplanner.engine.config.Charger;
import nl.enadeag.carchargeplanner.engine.config.Coordinates;
import nl.enadeag.carchargeplanner.engine.config.HomeSystem;
import nl.enadeag.carchargeplanner.engine.config.House;
import nl.enadeag.carchargeplanner.engine.config.PanelArray;
import nl.enadeag.carchargeplanner.engine.config.Percent;
import nl.enadeag.carchargeplanner.engine.config.Phases;
import nl.enadeag.carchargeplanner.engine.config.PlanningPreferences;
import nl.enadeag.carchargeplanner.engine.forecast.ForecastStep;
import nl.enadeag.carchargeplanner.engine.plan.AwayTimes;
import nl.enadeag.carchargeplanner.engine.plan.GridAdvisor;
import nl.enadeag.carchargeplanner.engine.plan.Planner;
import nl.enadeag.carchargeplanner.engine.trip.Trip;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

/** The battery card's levels as the dashboard takes them from a plan the planner made. */
class DashboardBatteryTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Amsterdam");
    /** A Friday. */
    private static final LocalDate FRI = LocalDate.of(2026, 7, 3);
    private static final LocalDate SAT = FRI.plusDays(1);
    private static final LocalDate SUN = SAT.plusDays(1);
    private static final Offset<Double> CLOSE = within(1e-6);
    private static final PlanningPreferences PREFERENCES = new PlanningPreferences(new Percent(30), new Percent(80),
            0.85, 0.2, Duration.ofMinutes(45), Duration.ofMinutes(15));

    private static HomeSystem home(double batteryKwh, double kmPerDay) {
        return new HomeSystem(new Coordinates(51.0, 5.0), Optional.empty(), ZONE,
                List.of(new PanelArray("", 4.8, 35, -45, 0.14)), new House(0.6), new Car(batteryKwh, 18, kmPerDay),
                new Charger(Phases.ONE, 6, 32, 230, 0.90));
    }

    private static Instant at(LocalDate day, int hour, int minute) {
        return day.atTime(hour, minute).atZone(ZONE).toInstant();
    }

    /** One day of hourly steps, 0 kW unless {@code powerKw} gives a value for the hour. */
    private static List<ForecastStep> forecast(LocalDate first, List<Map<Integer, Double>> powerKw) {
        var steps = new ArrayList<ForecastStep>();
        for (int day = 0; day < powerKw.size(); day++) {
            for (int hour = 0; hour < 24; hour++) {
                var from = at(first.plusDays(day), hour, 0);
                steps.add(new ForecastStep(from, from.plus(Duration.ofHours(1)),
                        powerKw.get(day).getOrDefault(hour, 0.0)));
            }
        }
        return steps;
    }

    private static DashboardView dashboard(HomeSystem home, List<ForecastStep> forecast, double levelPct,
            Instant now, Trip... trips) {
        var away = new AwayTimes(List.of(), List.of(), List.of(trips));
        var plan = new Planner(home, PREFERENCES, away).planWithTrips(forecast, new Percent(levelPct), now, List.of());
        var gridAdvice = new GridAdvisor(home, PREFERENCES, away).advise(plan, forecast, now);
        return DashboardView.of(new PlanParts(now, ZONE, plan.days(), 0.6, new Percent(levelPct), false, gridAdvice,
                plan.trips(), PREFERENCES.minimumPct(), PREFERENCES.targetPct(), List.of(), List.of(trips),
                Parts.source(now)));
    }

    /** 12 kW of sun from {@code fromHour} to {@code toHour}, so 9 to 17 charges a 100 kWh car from 31 % to 80 %. */
    private static Map<Integer, Double> sunFrom(int fromHour, int toHour) {
        var sun = new HashMap<Integer, Double>();
        for (int hour = fromHour; hour < toHour; hour++) {
            sun.put(hour, 12.0);
        }
        return sun;
    }

    /** The distance of a trip that takes {@code pct} of a 100 kWh battery, at 18 kWh per 100 km and 15 % on top. */
    private static double kmFor(double pct) {
        return pct / (0.18 * 1.15);
    }

    private static BatteryLevels battery(DashboardView view, LocalDate date) {
        return view.days().stream().filter(day -> day.name().date().equals(date)).findFirst().orElseThrow().battery();
    }

    @Test
    void theIssuesSaturdayShowsTheLevelTheEveningsGridChargeReaches() {
        // the 60 kWh car of 40 km a day (12 %): 150 km on Sunday take 51.75 %, so Sunday needs 30 + 51.75 + 12 =
        // 93.75 %; Saturday drives from 59 % to 47 % before its evening's grid charge brings it to 93.75 %, which the
        // apps show as 94 %
        var view = dashboard(home(60, 40), forecast(SAT, List.of(Map.of(), Map.of(), Map.of())), 59, at(SAT, 0, 0),
                new Trip(at(SUN, 8, 30), at(SUN, 18, 0), 150, false));
        var saturday = battery(view, SAT);
        assertThat(saturday.startPct().value()).isCloseTo(59, CLOSE);
        assertThat(saturday.afterPct().value()).isCloseTo(93.75, CLOSE);
        assertThat(saturday.afterTripPct()).isEmpty();
        var sunday = battery(view, SUN);
        assertThat(sunday.startPct().value()).isCloseTo(93.75, CLOSE);
        assertThat(sunday.afterPct().value()).isCloseTo(93.75, CLOSE);
        assertThat(sunday.afterTripPct().orElseThrow().value()).isCloseTo(30, CLOSE);
    }

    @Test
    void aDayWithTwoTripsShowsTheLevelTheSunReachesBetweenThemAndTheLevelAfterTheLast() {
        // a 100 kWh car without a daily distance: each 100 km trip takes 20.7 % and needs 50.7 %; the first leaves
        // with the 50 % the day starts with, is back with 29.3 %, and the sun charges it to the 80 % target before the
        // second leaves; the day ends at 59.3 %, after the second
        var sunny = Map.of(9, 12.0, 10, 12.0, 11, 12.0, 12, 12.0, 13, 12.0, 14, 12.0, 15, 12.0, 16, 12.0);
        var view = dashboard(home(100, 0), forecast(FRI, List.of(Map.of(), sunny)), 50, at(FRI, 0, 0),
                new Trip(at(SAT, 6, 0), at(SAT, 8, 0), 100, false),
                new Trip(at(SAT, 18, 0), at(SAT, 20, 0), 100, false));
        var saturday = battery(view, SAT);
        assertThat(saturday.startPct().value()).isCloseTo(50, CLOSE);
        assertThat(saturday.afterPct().value()).isCloseTo(80, CLOSE);
        assertThat(saturday.afterTripPct().orElseThrow().value()).isCloseTo(59.3, CLOSE);
        // the second departure is the day's highest level, so nothing is above every departure
        assertThat(saturday.departurePcts()).hasSize(2);
        assertThat(saturday.departurePcts().get(0).value()).isCloseTo(50, CLOSE);
        assertThat(saturday.departurePcts().get(1).value()).isCloseTo(80, CLOSE);
        assertThat(saturday.charging()).isEqualTo(BatteryLevels.Charging.NONE);
    }

    @Test
    void theIssuesDayLeavesAtItsStartAndChargesOnlyAfterTheReturn() {
        // the 100 kWh car leaves at 6:00 with the 50 % the day starts with, is back at 8:00 with 31 %, and the sun
        // charges it to the 80 % target
        var view = dashboard(home(100, 0), forecast(SAT, List.of(sunFrom(9, 17))), 50, at(SAT, 0, 0),
                new Trip(at(SAT, 6, 0), at(SAT, 8, 0), kmFor(19), false));

        var saturday = battery(view, SAT);

        assertThat(saturday.startPct().value()).isCloseTo(50, CLOSE);
        assertThat(saturday.departurePct().orElseThrow().value()).isCloseTo(50, CLOSE);
        assertThat(saturday.afterPct().value()).isCloseTo(80, CLOSE);
        assertThat(saturday.afterTripPct().orElseThrow().value()).isCloseTo(80, CLOSE);
        assertThat(saturday.charging()).isEqualTo(BatteryLevels.Charging.AFTER_RETURN);
    }

    @Test
    void withDailyDrivingTheDayAfterTheReturnStillReachesItsHighestLevelBeforeTheDriving() {
        // 50 km a day take 9 %: the trip needs 30 + 19 + 9 = 58 %, so the day starts at 60 %; the sun charges the
        // 41 % it is back with to 80 %, and the day ends at 71 %
        var view = dashboard(home(100, 50), forecast(SAT, List.of(sunFrom(9, 17))), 60, at(SAT, 0, 0),
                new Trip(at(SAT, 6, 0), at(SAT, 8, 0), kmFor(19), false));

        var saturday = battery(view, SAT);

        assertThat(saturday.departurePct().orElseThrow().value()).isCloseTo(60, CLOSE);
        assertThat(saturday.afterPct().value()).isCloseTo(80, CLOSE);
        assertThat(saturday.afterTripPct().orElseThrow().value()).isCloseTo(71, CLOSE);
        assertThat(saturday.charging()).isEqualTo(BatteryLevels.Charging.AFTER_RETURN);
    }

    @Test
    void theIssuesDayThatChargesBeforeTheTripLeavesAtTheLevelTheSunReaches() {
        var view = dashboard(home(100, 0), forecast(SAT, List.of(sunFrom(9, 17))), 50, at(SAT, 0, 0),
                new Trip(at(SAT, 18, 0), at(SAT, 20, 0), kmFor(19), false));

        var saturday = battery(view, SAT);

        assertThat(saturday.departurePct().orElseThrow().value()).isCloseTo(80, CLOSE);
        assertThat(saturday.afterPct().value()).isCloseTo(80, CLOSE);
        assertThat(saturday.afterTripPct().orElseThrow().value()).isCloseTo(61, CLOSE);
        assertThat(saturday.charging()).isEqualTo(BatteryLevels.Charging.BEFORE_TRIP);
    }

    @Test
    void aDayThatChargesBeforeTheTripAndAfterTheReturnLeavesBelowItsHighestLevel() {
        // an hour of sun before the 10:00 departure charges at 32 A, 7.36 kW, 90 % of which is 6.624 % of the
        // battery; the afternoon's sun after the return charges it to the target
        var sun = sunFrom(12, 19);
        sun.put(9, 12.0);
        var view = dashboard(home(100, 0), forecast(SAT, List.of(sun)), 50, at(SAT, 0, 0),
                new Trip(at(SAT, 10, 0), at(SAT, 11, 0), kmFor(19), false));

        var saturday = battery(view, SAT);

        assertThat(saturday.departurePct().orElseThrow().value()).isCloseTo(56.624, CLOSE);
        assertThat(saturday.afterPct().value()).isCloseTo(80, CLOSE);
        assertThat(saturday.charging()).isEqualTo(BatteryLevels.Charging.BEFORE_TRIP_AND_AFTER_RETURN);
    }

    @Test
    void aGridChargeBeforeTheDepartureIsChargingBeforeTheTrip() {
        // no sun: the trip needs 30 + 19 = 49 %, so the 9 % the 40 % start lacks come from the grid that day
        var view = dashboard(home(100, 0), forecast(SAT, List.of(Map.of())), 40, at(SAT, 0, 0),
                new Trip(at(SAT, 18, 0), at(SAT, 20, 0), kmFor(19), false));

        var saturday = battery(view, SAT);

        assertThat(saturday.departurePct().orElseThrow().value()).isCloseTo(49, CLOSE);
        assertThat(saturday.afterPct().value()).isCloseTo(49, CLOSE);
        assertThat(saturday.afterTripPct().orElseThrow().value()).isCloseTo(30, CLOSE);
        assertThat(saturday.charging()).isEqualTo(BatteryLevels.Charging.BEFORE_TRIP);
    }

    @Test
    void aLaterTripsGridChargeThatEveningIsChargingAfterTheReturn() {
        // the issue's Saturday of #508 with a short trip in the morning: the 60 kWh car leaves with its 59 %, and
        // the evening's grid charge for Sunday's trip brings it to 93.75 %
        var view = dashboard(home(60, 40), forecast(SAT, List.of(Map.of(), Map.of(), Map.of())), 59, at(SAT, 0, 0),
                new Trip(at(SAT, 8, 0), at(SAT, 9, 0), 20, false),
                new Trip(at(SUN, 8, 30), at(SUN, 18, 0), 150, false));

        var saturday = battery(view, SAT);

        assertThat(saturday.departurePct().orElseThrow().value()).isCloseTo(59, CLOSE);
        assertThat(saturday.afterPct().value()).isCloseTo(93.75, CLOSE);
        assertThat(saturday.afterTripPct().orElseThrow().value()).isCloseTo(93.75, CLOSE);
        assertThat(saturday.charging()).isEqualTo(BatteryLevels.Charging.AFTER_RETURN);
    }

    @Test
    void aDayWithTwoTripsChargesAfterTheReturnWhenItEndsAboveBothDepartures() {
        // two 50 km trips in the morning, then the sun charges the car to the 80 % target
        var view = dashboard(home(100, 0), forecast(SAT, List.of(sunFrom(10, 18))), 60, at(SAT, 0, 0),
                new Trip(at(SAT, 6, 0), at(SAT, 7, 0), 50, false),
                new Trip(at(SAT, 8, 0), at(SAT, 9, 0), 50, false));

        var saturday = battery(view, SAT);

        // each trip takes 50 * 0.207 = 10.35 %
        assertThat(saturday.departurePcts()).hasSize(2);
        assertThat(saturday.departurePcts().get(0).value()).isCloseTo(60, CLOSE);
        assertThat(saturday.departurePcts().get(1).value()).isCloseTo(49.65, CLOSE);
        assertThat(saturday.afterPct().value()).isCloseTo(80, CLOSE);
        assertThat(saturday.charging()).isEqualTo(BatteryLevels.Charging.AFTER_RETURN);
    }

    @Test
    void aTripBackOnALaterDayNeverChargesAfterTheReturnOnItsDepartureDay() {
        // the car is away in Saturday's sun
        var view = dashboard(home(100, 0), forecast(SAT, List.of(sunFrom(9, 17), sunFrom(9, 17))), 60,
                at(SAT, 0, 0), new Trip(at(SAT, 8, 0), at(SUN, 12, 0), kmFor(19), false));

        var saturday = battery(view, SAT);

        assertThat(saturday.afterPct().value()).isCloseTo(60, CLOSE);
        assertThat(saturday.afterTripPct().orElseThrow().value()).isCloseTo(41, CLOSE);
        assertThat(saturday.charging()).isEqualTo(BatteryLevels.Charging.NONE);
    }
}
