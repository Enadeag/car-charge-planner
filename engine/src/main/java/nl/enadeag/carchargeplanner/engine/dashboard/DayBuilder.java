package nl.enadeag.carchargeplanner.engine.dashboard;

import nl.enadeag.carchargeplanner.engine.config.Percent;
import nl.enadeag.carchargeplanner.engine.plan.DayPlan;
import nl.enadeag.carchargeplanner.engine.plan.GridAdvice;
import nl.enadeag.carchargeplanner.engine.plan.Window;
import nl.enadeag.carchargeplanner.engine.trip.Trip;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The days of one plan as their cards and battery bars show them, in the home's time zone: the windows and figures,
 * the kind, why a day has no window, the battery levels and the assumed level's note.
 */
final class DayBuilder {

    /** Less grid energy than this is shown as none: it is within the forecast's precision. */
    private static final double NO_GRID_KWH = 0.05;

    private final ZoneId zone;
    private final LocalDate today;
    /** The battery level the plan assumed; null when the owner entered one. */
    private final Percent assumed;
    /** The day the grid advice is about; null when the plan gives none. */
    private final LocalDate gridDay;
    private final Set<LocalDate> departures;
    /** The trips made with this car, which keep it away on the days from their departure to their return. */
    private final List<Trip> trips;

    DayBuilder(PlanParts parts) {
        this.zone = parts.zone();
        this.today = parts.madeAt().atZone(zone).toLocalDate();
        this.assumed = parts.batteryAssumed() ? parts.batteryLevelPct() : null;
        this.gridDay = parts.gridAdvice().map(GridAdvice::day).orElse(null);
        this.departures = parts.tripAdvice().stream()
                .map(advice -> advice.trip().firstDay(zone))
                .collect(Collectors.toSet());
        this.trips = parts.trips().stream().filter(trip -> !trip.notWithThisCar()).toList();
    }

    /**
     * @return the day, named from today
     */
    DashboardDay day(DayPlan day, DayChart chart) {
        var date = day.date();
        var name = DayName.of(date, today);
        var assumedToday = Optional.ofNullable(date.equals(today) ? assumed : null);
        var windows = day.windows();
        if (windows.isEmpty()) {
            var reason = noWindow(day);
            return new DashboardDay(name, Optional.empty(), List.of(), Optional.of(reason), battery(day), assumedToday,
                    chart);
        }
        return new DashboardDay(name, Optional.of(main(windows.get(0))), windows.subList(1, windows.size()),
                Optional.empty(), battery(day), assumedToday, chart);
    }

    private static MainCharge main(Window window) {
        double grid = window.importKwh();
        return new MainCharge(window, grid >= NO_GRID_KWH ? OptionalDouble.of(grid) : OptionalDouble.empty());
    }

    /**
     * The grid advice's day says so whatever the trips; the departure comes before being away on the trip. Any other
     * day needs no charging when the level it starts from, rounded half up as it is shown, reaches the level the plan
     * charges up to, so the card never contradicts the number the owner sees.
     */
    private NoWindowReason noWindow(DayPlan day) {
        var date = day.date();
        if (date.equals(gridDay)) {
            return NoWindowReason.GRID;
        }
        if (departures.contains(date)) {
            return NoWindowReason.DEPARTURE;
        }
        if (trips.stream().anyMatch(trip -> trip.covers(date, zone))) {
            return NoWindowReason.AWAY;
        }
        if (Math.round(day.startLevelPct().value()) >= day.wholeChargeToPct().value()) {
            return NoWindowReason.NOT_NEEDED;
        }
        return NoWindowReason.TOO_LITTLE_SOLAR;
    }

    private BatteryLevels battery(DayPlan day) {
        if (departures.contains(day.date())) {
            return new BatteryLevels(day.startLevelPct(), day.levelAfterChargingPct(),
                    Optional.of(day.endLevelPct()));
        }
        return new BatteryLevels(day.startLevelPct(), day.levelAfterChargingPct(), Optional.empty());
    }
}
