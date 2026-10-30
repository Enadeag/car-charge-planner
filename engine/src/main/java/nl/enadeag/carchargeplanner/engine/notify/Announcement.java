package nl.enadeag.carchargeplanner.engine.notify;

import nl.enadeag.carchargeplanner.engine.config.Percent;
import nl.enadeag.carchargeplanner.engine.config.Phases;
import nl.enadeag.carchargeplanner.engine.plan.Window;
import java.time.ZoneId;
import java.util.Optional;

/**
 * A charge window the owner is told about shortly before it starts.
 *
 * @param kind whether it is a solar window or the grid charge of the grid advice
 * @param window the window, with the current to set and the energy it brings
 * @param zone the time zone of the owner's home, in which the window's times are meant
 * @param phases the phases the charger charges on
 * @param minimumPct the minimum charge level of the battery
 * @param chargeLimitPct the charge limit to set in the car or the charger, present exactly for a grid charge
 */
public record Announcement(AnnouncementKind kind, Window window, ZoneId zone, Phases phases, Percent minimumPct,
        Optional<Percent> chargeLimitPct) implements Notice {

    /**
     * Checks that a grid charge, and only a grid charge, has a charge limit.
     *
     * @throws IllegalArgumentException when a grid charge has no charge limit, or a solar window has one
     */
    public Announcement {
        if (chargeLimitPct.isPresent() != (kind == AnnouncementKind.GRID_CHARGE)) {
            throw new IllegalArgumentException("an announcement has a charge limit exactly when it is a grid charge");
        }
    }
}
