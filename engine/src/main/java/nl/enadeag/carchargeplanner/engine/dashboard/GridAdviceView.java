package nl.enadeag.carchargeplanner.engine.dashboard;

import nl.enadeag.carchargeplanner.engine.config.Percent;
import java.util.Optional;

/**
 * The plan's grid advice: how much to charge from the grid, when, and why, with its days named from the day the plan
 * was made.
 *
 * @param day the day the battery would end below the minimum charge level without the grid charge, never null
 * @param energyKwh how much to charge from the grid, in kWh, finite and more than 0
 * @param endLevelPct the battery level that day would end at without the grid charge, finite; the dashboard checks
 *     that it is below the plan's minimum; not a {@link Percent}, as it goes below 0 when the driving needs more than
 *     the battery holds
 * @param window the window to charge in, with its day and the charge limit to set; never null, empty when no day up
 *     to {@code day} has room for the charge
 */
public record GridAdviceView(DayName day, double energyKwh, double endLevelPct, Optional<GridWindow> window) {

    /**
     * Checks the energy, the end level and the window's day.
     *
     * @throws IllegalArgumentException when the energy is not finite and more than 0, when the end level is not finite,
     *     or when the window is on a later day than {@code day}
     */
    public GridAdviceView {
        if (!(energyKwh > 0) || Double.isInfinite(energyKwh)) {
            throw new IllegalArgumentException("the grid energy must be finite and more than 0, not " + energyKwh
                    + " kWh");
        }
        if (Double.isNaN(endLevelPct) || Double.isInfinite(endLevelPct)) {
            throw new IllegalArgumentException("the end level must be finite, not " + endLevelPct + "%");
        }
        if (window.isPresent() && window.get().day().date().isAfter(day.date())) {
            throw new IllegalArgumentException("the grid charge comes before the day it is for, not on "
                    + window.get().day() + " for " + day);
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
}
