package nl.enadeag.carchargeplanner.engine.dashboard;

import nl.enadeag.carchargeplanner.engine.plan.DayPlan;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

/**
 * The parts of a plan that the dashboard is made from. {@code Outlook.Planned} hands them over, since
 * {@code engine.dashboard} does not reach up into the engine's root package; the later parts of #341 add their parts
 * to this record.
 *
 * @param madeAt when the plan was made, never null; its day in {@code zone} is today, and on today's chart it is "now"
 * @param zone the home's time zone, never null, in which the days and times are meant
 * @param days the plan of each day, today first, never null; may be empty
 * @param baseloadKw the baseload in kW, 0 or more
 */
public record PlanParts(Instant madeAt, ZoneId zone, List<DayPlan> days, double baseloadKw) {

    /** Keeps a copy of the days. */
    public PlanParts {
        days = List.copyOf(days);
    }
}
