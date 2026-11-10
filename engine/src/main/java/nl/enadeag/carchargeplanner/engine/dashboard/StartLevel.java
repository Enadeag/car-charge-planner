package nl.enadeag.carchargeplanner.engine.dashboard;

import nl.enadeag.carchargeplanner.engine.config.Percent;

/**
 * The battery level the plan starts from, and whether it is assumed.
 *
 * @param levelPct the battery level, never null
 * @param assumed true while the level is assumed, false when the owner entered it
 */
public record StartLevel(Percent levelPct, boolean assumed) {
}
