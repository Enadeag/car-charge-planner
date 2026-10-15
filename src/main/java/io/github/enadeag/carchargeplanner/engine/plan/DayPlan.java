package io.github.enadeag.carchargeplanner.engine.plan;

import java.time.LocalDate;
import java.util.List;

/**
 * One day of the plan, assuming the owner follows it.
 *
 * @param date the local date
 * @param socStartPct the battery level at the start of the day in percent
 * @param windows the charge windows of the day in time order, possibly none
 * @param socAfterChargingPct the battery level in percent once the windows are done
 * @param socEndOfDayPct the battery level at the end of the day in percent, 0 or more
 * @param drivingPct the part of the battery driving takes off the day in percent; unlike the end level it is not
 *     stopped at 0
 */
public record DayPlan(
        LocalDate date,
        double socStartPct,
        List<Window> windows,
        double socAfterChargingPct,
        double socEndOfDayPct,
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
