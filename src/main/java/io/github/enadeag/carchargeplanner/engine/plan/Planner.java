package io.github.enadeag.carchargeplanner.engine.plan;

import io.github.enadeag.carchargeplanner.engine.config.HomeSystem;
import io.github.enadeag.carchargeplanner.engine.config.PlanningPreferences;
import io.github.enadeag.carchargeplanner.engine.forecast.ForecastStep;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Plans the charge windows for each day of the forecast. */
public final class Planner {

    private final HomeSystem system;
    private final PlanningPreferences preferences;
    private final ZoneId zone;
    private final WindowFinder finder;

    public Planner(HomeSystem system, PlanningPreferences preferences) {
        this.system = system;
        this.preferences = preferences;
        this.zone = system.timezone();
        this.finder = new WindowFinder(system, preferences);
    }

    /**
     * Day by day from today, assuming the owner follows the plan.
     *
     * <p>Driving is taken off at the end of each day, so a day's charging starts from the level the day before left.
     * Today only loses the part of a day's driving that is still ahead.
     */
    public List<DayPlan> plan(List<ForecastStep> forecast, double socPct, Instant now) {
        if (forecast.isEmpty()) {
            return List.of();
        }
        var runsByDay = WindowFinder.byDay(finder.runs(forecast, now), zone);
        var today = now.atZone(zone).toLocalDate();
        var days = new ArrayList<DayPlan>();
        double soc = socPct;
        for (var day = today; !day.isAfter(lastDay(forecast)); day = day.plusDays(1)) {
            var windows = finder.windows(runsByDay.getOrDefault(day, List.of()), energyNeededKwh(soc));
            double gainedPct = windows.stream().mapToDouble(Window::solarKwh).sum()
                    * system.charger().efficiency() / system.car().usableBatteryKwh() * 100;
            double after = Math.max(soc, Math.min(preferences.targetPct(), soc + gainedPct));
            double driving = dailyDrivingPct() * (day.equals(today) ? shareOfDayLeft(now) : 1);
            double endOfDay = Math.max(0, after - driving);
            days.add(new DayPlan(day, soc, windows, after, endOfDay, driving));
            soc = endOfDay;
        }
        return days;
    }

    /** Grid-side energy (kWh) to bring the battery from the level to the target. */
    double energyNeededKwh(double socPct) {
        double batteryKwh = Math.max(0, preferences.targetPct() - socPct) / 100 * system.car().usableBatteryKwh();
        return batteryKwh / system.charger().efficiency();
    }

    private double dailyDrivingPct() {
        var car = system.car();
        return car.kmPerDay() * car.consumptionKwhPer100km() / 100 / car.usableBatteryKwh() * 100;
    }

    /** A step that ends exactly at midnight belongs to the day before. */
    private LocalDate lastDay(List<ForecastStep> forecast) {
        var end = forecast.stream().map(ForecastStep::end).max(Comparator.naturalOrder()).orElseThrow();
        return end.minusNanos(1).atZone(zone).toLocalDate();
    }

    /** The share of today still to come: the driving already done is in the battery level. */
    private double shareOfDayLeft(Instant now) {
        var today = now.atZone(zone).toLocalDate();
        var start = today.atStartOfDay(zone).toInstant();
        var end = today.plusDays(1).atStartOfDay(zone).toInstant();
        return Math.clamp(Hours.between(now, end) / Hours.between(start, end), 0.0, 1.0);
    }
}
