package nl.enadeag.carchargeplanner.engine.plan;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Advice to charge from the grid: the battery would end a day below the minimum charge level.
 *
 * @param day the first day that would end below the minimum
 * @param socEndPct the level at the end of that day without the grid charge; below 0 when the driving needs more than
 *     the battery holds
 * @param deficitPct how far that is below the minimum
 * @param energyKwh the wall-side energy that brings the end of that day up to the minimum
 * @param window the window to charge in, on that day or the latest day before it with room; empty when no day has room
 */
public record GridAdvice(LocalDate day, double socEndPct, double deficitPct, double energyKwh, Optional<Window> window) {
}
