package io.github.enadeag.carchargeplanner.engine.plan;

import io.github.enadeag.carchargeplanner.engine.config.Percent;
import java.time.LocalDate;
import java.util.List;

/**
 * One day of the plan, assuming the owner follows it.
 *
 * @param date the local date
 * @param socStartPct the battery level at the start of the day
 * @param windows the charge windows of the day in time order, possibly none
 * @param socAfterChargingPct the battery level once the windows are done
 * @param socEndOfDayPct the battery level at the end of the day; 0 when driving takes more than is left
 * @param drivingPct the part of the battery driving takes off the day in percent, 0 or more; more than 100 when the
 *     driving needs more than the battery holds
 */
public record DayPlan(
        LocalDate date,
        Percent socStartPct,
        List<Window> windows,
        Percent socAfterChargingPct,
        Percent socEndOfDayPct,
        double drivingPct) {

    /**
     * Copies the list of windows.
     *
     * @throws NullPointerException when the windows are null or hold null
     */
    public DayPlan {
        windows = List.copyOf(windows);
    }
}
