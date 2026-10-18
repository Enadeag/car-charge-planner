package io.github.enadeag.carchargeplanner.engine.plan;

import io.github.enadeag.carchargeplanner.engine.config.HomeSystem;
import io.github.enadeag.carchargeplanner.engine.config.Percent;
import io.github.enadeag.carchargeplanner.engine.config.PlanningPreferences;
import io.github.enadeag.carchargeplanner.engine.forecast.ForecastStep;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Plans the charge windows for each day of the forecast, outside the times the car is away. */
public final class Planner {

    private final HomeSystem system;
    private final PlanningPreferences preferences;
    private final AwayTimes away;
    private final ZoneId zone;
    private final WindowFinder finder;

    /**
     * Makes a planner for one home, one set of preferences and the times the car is away.
     *
     * @param system the home and equipment
     * @param preferences the planning preferences
     * @param away when the car is not at home; no window is planned in that time, {@link AwayTimes#NONE} for none
     */
    public Planner(HomeSystem system, PlanningPreferences preferences, AwayTimes away) {
        this.system = system;
        this.preferences = preferences;
        this.away = away;
        this.zone = system.timezone();
        this.finder = new WindowFinder(system, preferences);
    }

    /**
     * Day by day from today, assuming the owner follows the plan.
     *
     * <p>Driving is taken off at the end of each day, so a day's charging starts from the level the day before left.
     * Today only loses the part of a day's driving that is still ahead.
     *
     * <p>An announced window that is running now is kept as it was announced, with the same power and end, so the
     * advice does not change while the owner charges at the power they set. Only its remaining energy counts.
     *
     * @param forecast the forecast to plan on, in any order; empty gives an empty plan
     * @param socPct the battery level now
     * @param now the current moment; nothing is planned before it
     * @param announced the windows the owner has been told about, possibly none
     * @return one plan for each day from today to the last day of the forecast, in date order
     */
    public List<DayPlan> plan(List<ForecastStep> forecast, Percent socPct, Instant now, List<Window> announced) {
        if (forecast.isEmpty()) {
            return List.of();
        }
        var running = announced.stream()
                .filter(window -> window.isRunningAt(now))
                .map(window -> window.remainingAfter(now))
                .toList();
        var runsByDay = WindowFinder.byDay(runsBeside(forecast, now, running), zone);
        var stepsByDay = stepsByDay(forecast);
        var days = new ArrayList<DayPlan>();
        var soc = socPct;
        for (var date = today(now); !date.isAfter(lastDay(forecast)); date = date.plusDays(1)) {
            var day = planDay(date, soc, runsByDay.getOrDefault(date, List.of()), running, now,
                    stepsByDay.getOrDefault(date, List.of()));
            days.add(day);
            soc = day.socEndOfDayPct();
        }
        return days;
    }

    /**
     * The runs outside the away time and the running windows; the sun just before or after a running window belongs
     * to it, not to a second window.
     */
    private List<Run> runsBeside(List<ForecastStep> forecast, Instant now, List<Window> running) {
        var unavailable = new ArrayList<>(away.during(forecast, zone));
        running.forEach(window -> unavailable.add(new BlockedTime(window.start(), window.end())));
        return finder.runs(forecast, now, unavailable).stream()
                .filter(run -> running.stream().noneMatch(window -> touches(run, window)))
                .toList();
    }

    private static boolean touches(Run run, Window window) {
        return run.start().equals(window.end()) || run.end().equals(window.start());
    }

    private Map<LocalDate, List<ForecastStep>> stepsByDay(List<ForecastStep> forecast) {
        return forecast.stream()
                .sorted(Comparator.comparing(ForecastStep::start))
                .collect(Collectors.groupingBy(step -> step.start().atZone(zone).toLocalDate(), Collectors.toList()));
    }

    private DayPlan planDay(LocalDate date, Percent socPct, List<Run> runs, List<Window> running, Instant now,
            List<ForecastStep> steps) {
        var kept = running.stream().filter(window -> window.start().atZone(zone).toLocalDate().equals(date)).toList();
        double neededKwh = Math.max(0, energyNeededKwh(socPct) - solarKwh(kept));
        var windows = new ArrayList<>(kept);
        windows.addAll(finder.windows(runs, neededKwh));
        windows.sort(Comparator.comparing(Window::start));

        double gainedPct = solarKwh(windows) * system.charger().efficiency() / system.car().usableBatteryKwh() * 100;
        double after = Math.max(socPct.value(), Math.min(preferences.targetPct().value(), socPct.value() + gainedPct));
        double driving = dailyDrivingPct() * (date.equals(today(now)) ? shareOfDayLeft(now) : 1);
        var end = new Percent(Math.max(0, after - driving));
        return new DayPlan(date, socPct, windows, new Percent(after), end, driving, steps);
    }

    /**
     * The energy to bring the battery from a level to the target charge level.
     *
     * @param socPct the battery level to start from
     * @return the grid-side energy in kWh, 0 when the level is at or above the target
     */
    double energyNeededKwh(Percent socPct) {
        double missingPct = Math.max(0, preferences.targetPct().value() - socPct.value());
        double batteryKwh = missingPct / 100 * system.car().usableBatteryKwh();
        return batteryKwh / system.charger().efficiency();
    }

    private double dailyDrivingPct() {
        var car = system.car();
        return car.kmPerDay() * car.consumptionKwhPer100km() / 100 / car.usableBatteryKwh() * 100;
    }

    private static double solarKwh(List<Window> windows) {
        return windows.stream().mapToDouble(Window::solarKwh).sum();
    }

    private LocalDate today(Instant now) {
        return now.atZone(zone).toLocalDate();
    }

    /** A step that ends exactly at midnight belongs to the day before. */
    private LocalDate lastDay(List<ForecastStep> forecast) {
        var end = forecast.stream().map(ForecastStep::end).max(Comparator.naturalOrder()).orElseThrow();
        return end.minusNanos(1).atZone(zone).toLocalDate();
    }

    /** The share of today still to come: the driving already done is in the battery level. */
    private double shareOfDayLeft(Instant now) {
        var today = today(now);
        var start = today.atStartOfDay(zone).toInstant();
        var end = today.plusDays(1).atStartOfDay(zone).toInstant();
        return Math.clamp(Hours.between(now, end) / Hours.between(start, end), 0.0, 1.0);
    }
}
