package io.github.enadeag.carchargeplanner.engine.notify;

import io.github.enadeag.carchargeplanner.engine.config.Percent;
import io.github.enadeag.carchargeplanner.engine.config.Phases;
import io.github.enadeag.carchargeplanner.engine.plan.Window;
import java.time.ZoneId;

/**
 * A charge window the owner is told about shortly before it starts.
 *
 * @param kind whether it is a window on the sun or the grid charge of the grid advice
 * @param window the window, with the current to set and the energy it brings
 * @param zone the time zone of the owner's home, in which the window's times are meant
 * @param phases the phases the charger charges on
 * @param minimumPct the minimum charge level of the battery
 */
public record Announcement(AnnouncementKind kind, Window window, ZoneId zone, Phases phases, Percent minimumPct) {
}
