package nl.enadeag.carchargeplanner.engine.plan;

import java.util.List;

/**
 * The plan for the coming days, and what it does for each trip in them.
 *
 * @param days one plan for each day from today to the last day of the forecast, in date order; empty when there is no
 *     forecast
 * @param trips the advice for each trip that departs after now and before the forecast ends, in order of departure;
 *     possibly none
 */
public record Plan(List<DayPlan> days, List<TripAdvice> trips) {

    /**
     * Copies the lists.
     *
     * @throws NullPointerException when a list is null or holds null
     */
    public Plan {
        days = List.copyOf(days);
        trips = List.copyOf(trips);
    }
}
