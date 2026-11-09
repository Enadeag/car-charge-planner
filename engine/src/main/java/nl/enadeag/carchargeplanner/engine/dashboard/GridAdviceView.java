package nl.enadeag.carchargeplanner.engine.dashboard;

import nl.enadeag.carchargeplanner.engine.config.Percent;
import nl.enadeag.carchargeplanner.engine.plan.Window;
import java.util.Optional;

/**
 * The plan's grid advice as the dashboard shows it, on today's tab: how much to charge from the grid and when, at what
 * current and charge limit, and why, with its days named from the day the plan was made.
 *
 * @param day the day the battery would end below the minimum charge level without the grid charge, never null
 * @param energyKwh how much to charge from the grid, in kWh, finite and more than 0
 * @param endLevelPct the battery level that day would end at without the grid charge, finite and below the minimum;
 *     not a {@link Percent}, as it goes below 0 when the driving needs more than the battery holds
 * @param minimumPct the minimum charge level it would fall below: the plan's, never null
 * @param charge the window to charge in, with its day and the charge limit to set; never null, empty when no day up
 *     to {@code day} has room for the charge
 */
public record GridAdviceView(DayName day, double energyKwh, double endLevelPct, Percent minimumPct,
        Optional<Charge> charge) {

    /**
     * Checks the energy, the end level and the window's day.
     *
     * @throws IllegalArgumentException when the energy is not finite and more than 0, when the end level is not finite
     *     and below the minimum, or when the window is on a later day than {@code day}
     */
    public GridAdviceView {
        if (!(energyKwh > 0) || Double.isInfinite(energyKwh)) {
            throw new IllegalArgumentException("the grid energy must be finite and more than 0, not " + energyKwh
                    + " kWh");
        }
        if (!(endLevelPct < minimumPct.value()) || Double.isInfinite(endLevelPct)) {
            throw new IllegalArgumentException("the end level must be finite and below the minimum of "
                    + minimumPct.value() + "%, not " + endLevelPct + "%");
        }
        if (charge.isPresent() && charge.get().day().date().isAfter(day.date())) {
            throw new IllegalArgumentException("the grid charge comes before the day it is for, not on "
                    + charge.get().day() + " for " + day);
        }
    }

    /**
     * Whether the battery would run empty rather than only end below the minimum.
     *
     * @return true when the end level is 0 or less
     */
    public boolean runsEmpty() {
        return endLevelPct <= 0;
    }

    /**
     * The window to take the grid charge in.
     *
     * @param window the window, never null, with the current, power and times to set
     * @param day the window's day, by its start in the home's time zone, never null
     * @param chargeLimitPct the charge limit to set in the car or the charger, so the charge stops when the window's
     *     energy is in, never null
     */
    public record Charge(Window window, DayName day, Percent chargeLimitPct) {
    }
}
