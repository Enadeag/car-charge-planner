package nl.enadeag.carchargeplanner.engine.plan;

import nl.enadeag.carchargeplanner.engine.config.Percent;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Advice to charge from the grid: the battery would end a day below the minimum charge level.
 *
 * @param day the first day that would end below the minimum
 * @param endLevelPct the level at the end of that day without the grid charge; below 0 when the driving needs more than
 *     the battery holds
 * @param deficitPct how far that is below the minimum
 * @param energyKwh the wall-side energy that brings the end of that day up to the minimum
 * @param window the window to charge in, on that day or the latest day before it with room, starting within the
 *     charging hours; empty when no day has room
 * @param chargeLimitPct the charge limit to set in the car or the charger, so the charge stops when the window's energy
 *     is in: a whole percentage; present exactly when the window is
 */
public record GridAdvice(LocalDate day, double endLevelPct, double deficitPct, double energyKwh,
        Optional<Window> window, Optional<Percent> chargeLimitPct) {

    /**
     * Checks that the charge limit goes with the window.
     *
     * @throws IllegalArgumentException when there is a window without a charge limit, or a charge limit without one
     */
    public GridAdvice {
        if (window.isPresent() != chargeLimitPct.isPresent()) {
            throw new IllegalArgumentException("a grid advice has a charge limit exactly when it has a window");
        }
    }
}
