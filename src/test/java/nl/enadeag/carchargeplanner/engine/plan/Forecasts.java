package nl.enadeag.carchargeplanner.engine.plan;

import nl.enadeag.carchargeplanner.engine.away.WeeklyAwayPeriod;
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
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** The system the planner tests plan for, and forecasts made by hand. */
final class Forecasts {

    /** It has the daylight saving dates the tests use: 2026-03-29 and 2026-10-25. */
    static final ZoneId TZ = ZoneId.of("Europe/Amsterdam");

    static final PanelArray PANELS = new PanelArray("", 4.8, 35, -45, 0.14);
    static final PlanningPreferences PREFERENCES = new PlanningPreferences(new Percent(30), new Percent(80), 0.85, 0.2,
            Duration.ofMinutes(45), Duration.ofMinutes(15));
    /** The example's weekly away period: Thursdays from 06:00 to 19:00. */
    static final AwayTimes WEEKLY_AWAY = new AwayTimes(
            List.of(new WeeklyAwayPeriod(DayOfWeek.THURSDAY, LocalTime.of(6, 0), LocalTime.of(19, 0))), List.of(),
            List.of());

    private Forecasts() {
    }

    /** A 4.8 kWp roof facing south-east, a 70 kWh car driving 600 km a week, a 32 A charger on one phase. */
    static HomeSystem example() {
        return system(600.0 / 7, Phases.ONE);
    }

    static HomeSystem exampleOnPhases(Phases phases) {
        return system(600.0 / 7, phases);
    }

    /** The example driving 70 km a day: 12.6 kWh, a round 18 % of the battery. */
    static HomeSystem golden() {
        return system(70, Phases.ONE);
    }

    static HomeSystem goldenOnThreePhases() {
        return system(70, Phases.THREE);
    }

    private static HomeSystem system(double kmPerDay, Phases phases) {
        return new HomeSystem(new Coordinates(51.0, 5.0), TZ, List.of(PANELS), new House(0.6),
                new Car(70, 18, kmPerDay), new Charger(phases, 6, 32, 230, 0.90));
    }

    static Instant at(LocalDate day, int hour) {
        return at(day, hour, 0);
    }

    static Instant at(LocalDate day, int hour, int minute) {
        return day.atTime(hour, minute).atZone(TZ).toInstant();
    }

    /**
     * One local day of hourly steps, 0 kW unless {@code powerKw} gives a value. The keys count real hours from local
     * midnight, so on a daylight saving day key 3 is 04:00 (23-hour day) or the second 02:00 (25-hour day).
     */
    static List<ForecastStep> hourly(LocalDate day, Map<Integer, Double> powerKw) {
        var start = day.atStartOfDay(TZ).toInstant();
        long hours = Duration.between(start, day.plusDays(1).atStartOfDay(TZ).toInstant()).toHours();
        var steps = new ArrayList<ForecastStep>();
        for (int index = 0; index < hours; index++) {
            var from = start.plus(Duration.ofHours(index));
            steps.add(new ForecastStep(from, from.plus(Duration.ofHours(1)), powerKw.getOrDefault(index, 0.0)));
        }
        return steps;
    }

    /** Days of sunshine shaped like a half sine from 06:00 to 18:00, given by their peak irradiance (W/m2). */
    static List<ForecastStep> bellDays(Map<LocalDate, Double> peakWm2) {
        var steps = new ArrayList<ForecastStep>();
        peakWm2.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(day -> steps.addAll(bellDay(day.getKey(), day.getValue())));
        return steps;
    }

    static List<ForecastStep> bellDay(LocalDate day, double peakWm2) {
        var steps = new ArrayList<ForecastStep>();
        for (var step : hourly(day, Map.of())) {
            var local = step.start().atZone(TZ);
            double hour = local.getHour() + local.getMinute() / 60.0 + 0.5;
            double irradiance = hour >= 6 && hour <= 18 ? peakWm2 * Math.sin(Math.PI * (hour - 6) / 12) : 0;
            double powerKw = PANELS.kwp() * irradiance / 1000 * (1 - PANELS.systemLosses());
            steps.add(new ForecastStep(step.start(), step.end(), powerKw));
        }
        return steps;
    }
}
