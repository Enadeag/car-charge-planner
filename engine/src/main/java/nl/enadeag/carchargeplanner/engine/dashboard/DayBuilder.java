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
    /** The battery level the plan assumed; empty when the owner entered one. */
    private final Optional<Percent> assumed;
    /** The day the grid advice is about; empty when the plan gives none. */
    private final Optional<LocalDate> gridDay;
    /** The days a trip the plan advises on leaves. */
    private final Set<LocalDate> departures;
    /** The trips made with this car, which keep it away on the days from their departure to their return. */
    private final List<Trip> trips;

    /**
     * Makes the days of one plan.
     *
     * @param parts the plan's parts, never null: when it was made, whose day in the home's time zone is today; the
     *     zone; the battery level and whether it is assumed; the grid advice, whose day is the grid's; the trip
     *     advice, whose trips' first days are departure days; and the stored trips, which keep the car away
     */
    DayBuilder(PlanParts parts) {
        this.zone = parts.zone();
        this.today = parts.madeAt().atZone(zone).toLocalDate();
        this.assumed = parts.batteryAssumed() ? Optional.of(parts.batteryLevelPct()) : Optional.empty();
        this.gridDay = parts.gridAdvice().map(GridAdvice::day);
        this.departures = parts.tripAdvice().stream()
                .map(advice -> advice.trip().firstDay(zone))
                .collect(Collectors.toSet());
        this.trips = parts.trips().stream().filter(trip -> !trip.notWithThisCar()).toList();
    }

    /**
     * One day as the dashboard shows it.
     *
     * @param day one of the plan's days, never null
     * @param chart the day's chart, never null
     * @return the day, named from today
     */
    DashboardDay day(DayPlan day, DayChart chart) {
        var date = day.date();
        var windows = day.windows();
        Optional<MainCharge> main = windows.isEmpty() ? Optional.empty() : Optional.of(main(windows.get(0)));
        var later = windows.isEmpty() ? List.<Window>of() : windows.subList(1, windows.size());
        var noWindow = windows.isEmpty() ? Optional.of(noWindow(date)) : Optional.<NoWindowReason>empty();
        return new DashboardDay(DayName.of(date, today), kind(main, noWindow), main, later, noWindow, battery(day),
                date.equals(today) ? assumed : Optional.empty(), chart);
    }

    private static MainCharge main(Window window) {
        double grid = window.importKwh();
        return new MainCharge(window, grid >= NO_GRID_KWH ? Optional.of(grid) : Optional.empty());
    }

    private static DayKind kind(Optional<MainCharge> main, Optional<NoWindowReason> noWindow) {
        if (main.isPresent()) {
            return DayKind.SOLAR;
        }
        return noWindow.equals(Optional.of(NoWindowReason.GRID)) ? DayKind.GRID : DayKind.NONE;
    }

    /** The grid advice's day says so whatever the trips; the departure comes before being away on the trip. */
    private NoWindowReason noWindow(LocalDate date) {
        if (gridDay.equals(Optional.of(date))) {
            return NoWindowReason.GRID;
        }
        if (departures.contains(date)) {
            return NoWindowReason.DEPARTURE;
        }
        if (trips.stream().anyMatch(trip -> trip.covers(date, zone))) {
            return NoWindowReason.AWAY;
        }
        return NoWindowReason.NOT_NEEDED;
    }

    private BatteryLevels battery(DayPlan day) {
        if (departures.contains(day.date())) {
            return BatteryLevels.leaving(day.startLevelPct(), day.levelAfterChargingPct(), day.endLevelPct());
        }
        return BatteryLevels.of(day.startLevelPct(), day.levelAfterChargingPct());
    }
}
