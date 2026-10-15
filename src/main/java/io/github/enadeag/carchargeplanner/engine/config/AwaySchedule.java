package io.github.enadeag.carchargeplanner.engine.config;

import io.github.enadeag.carchargeplanner.engine.away.RecurringAwayPeriod;
import java.util.List;

/**
 * The away periods that come back every week, as the config file's {@code away} section gives them. A file without
 * that section has none.
 *
 * @param recurring the weekly away periods, possibly none; several may fall on the same day
 */
public record AwaySchedule(List<RecurringAwayPeriod> recurring) {

    /** No away periods at all. */
    public static final AwaySchedule NONE = new AwaySchedule(List.of());

    /**
     * Copies the list.
     *
     * @throws NullPointerException when the list is null or holds null
     */
    public AwaySchedule {
        recurring = List.copyOf(recurring);
    }
}
