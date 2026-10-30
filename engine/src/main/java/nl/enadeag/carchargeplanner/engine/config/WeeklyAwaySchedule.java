package nl.enadeag.carchargeplanner.engine.config;

import nl.enadeag.carchargeplanner.engine.away.WeeklyAwayPeriod;
import java.util.List;

/**
 * The away periods that come back every week, as the config file's {@code away} section gives them. A file without
 * that section has none.
 *
 * @param weekly the weekly away periods, possibly none; several may fall on the same day
 */
public record WeeklyAwaySchedule(List<WeeklyAwayPeriod> weekly) {

    /** No away periods at all. */
    public static final WeeklyAwaySchedule NONE = new WeeklyAwaySchedule(List.of());

    /**
     * Copies the list.
     *
     * @throws NullPointerException when the list is null or holds null
     */
    public WeeklyAwaySchedule {
        weekly = List.copyOf(weekly);
    }
}
