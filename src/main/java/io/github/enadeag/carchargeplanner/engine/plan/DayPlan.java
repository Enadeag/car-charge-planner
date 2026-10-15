package io.github.enadeag.carchargeplanner.engine.plan;

import java.time.LocalDate;
import java.util.List;

/**
 * One day of the plan, assuming the owner follows it.
 *
 * @param drivingPct the driving taken off this day; the end of the day stops at 0 %, this keeps the true shortfall
 */
public record DayPlan(
        LocalDate date,
        double socStartPct,
        List<Window> windows,
        double socAfterChargingPct,
        double socEndOfDayPct,
        double drivingPct) {

    public DayPlan {
        windows = List.copyOf(windows);
    }
}
