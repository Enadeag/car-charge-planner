package nl.enadeag.carchargeplanner.engine.plan;

import nl.enadeag.carchargeplanner.engine.config.HomeSystem;
import nl.enadeag.carchargeplanner.engine.config.PlanningPreferences;
import nl.enadeag.carchargeplanner.engine.forecast.ForecastStep;
import nl.enadeag.carchargeplanner.engine.trip.Trip;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Advises a grid charge when the battery would end a day below the minimum charge level, on a day that is not a trip's.
 */
public final class GridAdvisor {

    /** A shortfall smaller than this is forecast noise, not worth advice. */
    private static final double MIN_DEFICIT_PCT = 1.0;

    private final HomeSystem system;
    private final PlanningPreferences preferences;
    private final AwayTimes away;
    private final ZoneId zone;
    private final GridPlacements placements;

    /**
     * Makes an advisor for one home, one set of preferences and the times the car is away.
     *
     * @param system the home and equipment
     * @param preferences the planning preferences, for the minimum charge level and the margin
     * @param away when the car is not at home; no grid charge is advised in that time, {@link AwayTimes#NONE} for
     *     none. The same times the {@link Planner} was given.
     */
    public GridAdvisor(HomeSystem system, PlanningPreferences preferences, AwayTimes away) {
        this.system = system;
        this.preferences = preferences;
        this.away = away;
        this.zone = system.timezone();
        this.placements = new GridPlacements(system, preferences);
    }

    /**
     * Advice for the first day that ends below the minimum after its planned solar charging and driving, so a dip that
     * solar charging fixes in time gives no advice. The days of a trip are left to its advice, and the window is placed
     * after the return of the trips before the dip and clear of their grid charges.
     *
     * @param plan the plan the {@link Planner} made from this forecast, with its trips
     * @param forecast the forecast the plan was made from, in time order
     * @param now the current moment; no window is placed before it
     * @return the advice, or empty when no day ends more than 1 percentage point below the minimum, as less is
     *     forecast noise, or the forecast is empty
     */
    public Optional<GridAdvice> advise(Plan plan, List<ForecastStep> forecast, Instant now) {
        var days = plan.days();
        var dip = days.stream()
                .filter(day -> plan.trips().stream().noneMatch(trip -> TripAdvice.covers(trip.trip(), day.date(), zone)))
                .filter(this::endsBelowMinimum)
                .findFirst();
        if (dip.isEmpty() || forecast.isEmpty()) {
            return Optional.empty();
        }
        var dipStart = dip.get().date().atStartOfDay(zone).toInstant();
        var notBefore = plan.trips().stream()
                .map(TripAdvice::trip)
                .filter(trip -> trip.departure().isBefore(dipStart))
                .map(Trip::returnTime)
                .reduce(now, Hours::latest);
        var tripGrid = plan.trips().stream()
                .flatMap(trip -> trip.grid().stream())
                .map(grid -> new BlockedTime(grid.window().start(), grid.window().end()))
                .toList();
        return Optional.of(advice(days, dip.get(), forecast, notBefore, tripGrid));
    }

    /**
     * Advice for a plan without trips.
     *
     * @param days the plan the {@link Planner} made from this forecast, for a car without trips
     * @param forecast the forecast the plan was made from, in time order
     * @param now the current moment; no window is placed before it
     * @return the advice, as {@link #advise(Plan, List, Instant)} gives it
     */
    Optional<GridAdvice> advise(List<DayPlan> days, List<ForecastStep> forecast, Instant now) {
        return advise(new Plan(days, List.of()), forecast, now);
    }

    private boolean endsBelowMinimum(DayPlan day) {
        return endLevelPct(day) < preferences.minimumPct().value() - MIN_DEFICIT_PCT;
    }

    /** The true level at the end of the day: the plan's own end level stops at 0 %. */
    private static double endLevelPct(DayPlan day) {
        return day.levelAfterChargingPct().value() - day.drivingPct();
    }

    private GridAdvice advice(List<DayPlan> days, DayPlan dip, List<ForecastStep> forecast, Instant notBefore,
            List<BlockedTime> tripGrid) {
        double deficitPct = preferences.minimumPct().value() - endLevelPct(dip);
        double energyKwh = deficitPct / 100 * system.car().usableBatteryKwh() / system.charger().efficiency();
        var daysUpToDip = days.subList(0, days.indexOf(dip) + 1);
        return new GridAdvice(dip.date(), endLevelPct(dip), deficitPct, energyKwh,
                latestPlacement(daysUpToDip, energyKwh, forecast, notBefore, tripGrid));
    }

    /** The best window on the last of the days that has room for it. A window does not cross midnight. */
    private Optional<Window> latestPlacement(List<DayPlan> days, double energyKwh, List<ForecastStep> forecast,
            Instant notBefore, List<BlockedTime> tripGrid) {
        var awayTime = away.during(forecast, zone);
        for (var day : days.reversed()) {
            var unavailable = new ArrayList<>(awayTime);
            unavailable.addAll(tripGrid);
            day.windows().forEach(window -> unavailable.add(new BlockedTime(window.start(), window.end())));
            var quarters = placements.quarterHours(day.date(), day.date(), forecast, unavailable);
            var window = bestPlacement(quarters, energyKwh, notBefore);
            if (window.isPresent()) {
                return window;
            }
        }
        return Optional.empty();
    }

    /**
     * The window that takes the least from the grid, so it uses the most leftover surplus. The currents are tried from
     * the lowest up and the start times from the earliest on, so on a tie the lowest current wins, and then the
     * earliest start.
     */
    private Optional<Window> bestPlacement(List<GridPlacements.QuarterHour> quarters, double energyKwh,
            Instant notBefore) {
        var charger = system.charger();
        Window best = null;
        for (int amps = charger.minAmps(); amps <= charger.maxAmps(); amps++) {
            for (var candidate : placements.placements(quarters, energyKwh, amps, notBefore)) {
                if (best == null || Charging.roundKwh(candidate.importKwh()) < Charging.roundKwh(best.importKwh())) {
                    best = candidate;
                }
            }
        }
        return Optional.ofNullable(best);
    }
}
