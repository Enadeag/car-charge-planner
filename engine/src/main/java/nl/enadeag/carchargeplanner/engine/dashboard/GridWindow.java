package nl.enadeag.carchargeplanner.engine.dashboard;

import nl.enadeag.carchargeplanner.engine.config.Percent;
import nl.enadeag.carchargeplanner.engine.plan.Window;

/**
 * The window to take the grid charge in.
 *
 * @param window the window, never null, with the current, power and times to set
 * @param day the window's day, by its start in the home's time zone, never null
 * @param chargeLimitPct the charge limit to set in the car or the charger, so the charge stops when the window's
 *     energy is in, never null
 */
public record GridWindow(Window window, DayName day, Percent chargeLimitPct) {
}
