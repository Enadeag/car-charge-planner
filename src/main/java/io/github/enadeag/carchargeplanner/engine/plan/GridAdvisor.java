package io.github.enadeag.carchargeplanner.engine.plan;

import io.github.enadeag.carchargeplanner.engine.config.HomeSystem;
import io.github.enadeag.carchargeplanner.engine.config.PlanningPreferences;
import io.github.enadeag.carchargeplanner.engine.forecast.ForecastStep;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Advises a grid charge when the battery would end a day below the minimum charge level. */
public final class GridAdvisor {

    private static final Duration SLOT = Duration.ofMinutes(15);
    private static final double SLOT_HOURS = 0.25;
    /** A shortfall smaller than this is forecast noise, not worth a warning. */
    private static final double MIN_DEFICIT_PCT = 1.0;
    private static final double EPS = 1e-9;

    private final HomeSystem system;
    private final PlanningPreferences preferences;
    private final ZoneId zone;

    public GridAdvisor(HomeSystem system, PlanningPreferences preferences) {
        this.system = system;
        this.preferences = preferences;
        this.zone = system.timezone();
    }

    /**
     * Advice for the first day that ends below the minimum after its planned solar charging and driving, so a dip that
     * the sun fixes in time gives no warning.
     *
     * @param days the plan the {@link Planner} made from this forecast
     */
    public Optional<GridAdvice> advise(List<DayPlan> days, List<ForecastStep> forecast, Instant now) {
        var dip = days.stream().filter(this::endsBelowMinimum).findFirst();
        if (dip.isEmpty() || forecast.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(advice(days, dip.get(), forecast, now));
    }

    private boolean endsBelowMinimum(DayPlan day) {
        return endPct(day) < preferences.minimumPct() - MIN_DEFICIT_PCT;
    }

    /** The true level at the end of the day: the plan's own end level stops at 0 %. */
    private static double endPct(DayPlan day) {
        return day.socAfterChargingPct() - day.drivingPct();
    }

    private GridAdvice advice(List<DayPlan> days, DayPlan dip, List<ForecastStep> forecast, Instant now) {
        double deficitPct = preferences.minimumPct() - endPct(dip);
        double energyKwh = deficitPct / 100 * system.car().usableBatteryKwh() / system.charger().efficiency();
        var daysUpToDip = days.subList(0, days.indexOf(dip) + 1);
        return new GridAdvice(dip.date(), endPct(dip), deficitPct, energyKwh,
                latestPlacement(daysUpToDip, energyKwh, forecast, now));
    }

    /** The best window on the last of the days that has room for it. A window does not cross midnight. */
    private Optional<Window> latestPlacement(List<DayPlan> days, double energyKwh, List<ForecastStep> forecast,
            Instant now) {
        for (var day : days.reversed()) {
            var window = bestPlacement(slots(day, forecast, now), energyKwh);
            if (window.isPresent()) {
                return window;
            }
        }
        return Optional.empty();
    }

    /** A quarter of an hour of one day. It is free when the forecast covers it, it lies ahead and no window uses it. */
    private record Slot(Instant start, Instant end, double usableKw, boolean free) {
    }

    /** The day's quarters of an hour in real time, so a daylight saving day has 92 or 100 of them. */
    private List<Slot> slots(DayPlan day, List<ForecastStep> forecast, Instant now) {
        var unavailable = day.windows().stream().map(window -> new Unavailable(window.start(), window.end())).toList();
        var end = day.date().plusDays(1).atStartOfDay(zone).toInstant();
        var slots = new ArrayList<Slot>();
        for (var start = day.date().atStartOfDay(zone).toInstant(); start.isBefore(end); start = start.plus(SLOT)) {
            slots.add(slot(start, forecast, unavailable, now));
        }
        return slots;
    }

    private Slot slot(Instant start, List<ForecastStep> forecast, List<Unavailable> unavailable, Instant now) {
        var end = start.plus(SLOT);
        var step = forecast.stream()
                .filter(candidate -> !candidate.start().isAfter(start) && candidate.end().isAfter(start))
                .findFirst();
        boolean free = step.isPresent()
                && !start.isBefore(now)
                && unavailable.stream().noneMatch(time -> time.overlaps(start, end));
        double usableKw = step.map(found -> Charging.usableKw(found, system, preferences)).orElse(0.0);
        return new Slot(start, end, usableKw, free);
    }

    /**
     * The window that takes the least from the grid, so it uses the most leftover sun. The currents and the start
     * times are tried from low to early, so on a tie the lowest current and then the earliest start win.
     */
    private Optional<Window> bestPlacement(List<Slot> slots, double energyKwh) {
        var charger = system.charger();
        Window best = null;
        for (int amps = charger.minAmps(); amps <= charger.maxAmps(); amps++) {
            for (var candidate : placements(slots, energyKwh, amps)) {
                if (best == null || Charging.roundKwh(candidate.importKwh()) < Charging.roundKwh(best.importKwh())) {
                    best = candidate;
                }
            }
        }
        return Optional.ofNullable(best);
    }

    /** Every stretch of free slots that delivers the energy at this current, as a window. */
    private List<Window> placements(List<Slot> slots, double energyKwh, int amps) {
        double powerKw = Charging.powerKw(amps, system.charger());
        double hours = energyKwh / powerKw;
        int count = (int) Math.ceil(hours / SLOT_HOURS - EPS);
        var placements = new ArrayList<Window>();
        for (int first = 0; first + count <= slots.size(); first++) {
            var stretch = slots.subList(first, first + count);
            if (stretch.stream().allMatch(Slot::free)) {
                placements.add(window(stretch, amps, powerKw, hours));
            }
        }
        return placements;
    }

    /** Charging for the hours over the slots: the whole of each, and of the last only the part still needed. */
    private Window window(List<Slot> stretch, int amps, double powerKw, double hours) {
        double solar = 0;
        double imported = 0;
        int last = stretch.size() - 1;
        for (int i = 0; i <= last; i++) {
            double slotHours = i < last ? SLOT_HOURS : hours - last * SLOT_HOURS;
            double usableKw = stretch.get(i).usableKw();
            solar += Math.min(powerKw, usableKw) * slotHours;
            imported += Math.max(0, powerKw - usableKw) * slotHours;
        }
        var start = stretch.getFirst().start();
        var end = Hours.earliest(Hours.ceilToFiveMinutes(Hours.plus(start, hours), zone), stretch.getLast().end());
        return new Window(start, end, amps, powerKw, solar, imported);
    }
}
