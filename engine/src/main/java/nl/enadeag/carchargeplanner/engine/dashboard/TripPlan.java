package nl.enadeag.carchargeplanner.engine.dashboard;

import nl.enadeag.carchargeplanner.engine.config.Percent;
import java.util.List;

/** What the plan does for a trip, in one of five cases. */
public sealed interface TripPlan {

    /**
     * The plan charges enough for the trip already: up to the target charge level, or the battery holds it.
     *
     * @param departureLevelPct the battery level when the trip leaves, never null
     */
    record Enough(Percent departureLevelPct) implements TripPlan {
    }

    /**
     * Charge on solar power above the target charge level, up to the charge limit.
     *
     * @param chargeLimitPct the level to charge to, which is also the charge limit to set, never null
     * @param days the solar windows by day, in time order, never null; never empty
     */
    record Solar(Percent chargeLimitPct, List<SolarDay> days) implements TripPlan {

        /**
         * Keeps a copy of the days.
         *
         * @throws IllegalArgumentException when there are no days
         */
        public Solar {
            days = List.copyOf(days);
            if (days.isEmpty()) {
                throw new IllegalArgumentException("a charge on solar power has solar windows");
            }
        }
    }

    /**
     * Charge on solar power, then from the grid up to the charge limit.
     *
     * @param chargeLimitPct the level to charge to, which is also the charge limit to set, never null
     * @param days the solar windows by day, in time order, never null; never empty
     * @param grid the grid charge, never null
     */
    record SolarThenGrid(Percent chargeLimitPct, List<SolarDay> days, TripGridStart grid) implements TripPlan {

        /**
         * Keeps a copy of the days.
         *
         * @throws IllegalArgumentException when there are no days
         */
        public SolarThenGrid {
            days = List.copyOf(days);
            if (days.isEmpty()) {
                throw new IllegalArgumentException("a charge on solar power and then from the grid has solar windows");
            }
        }
    }

    /**
     * Charge from the grid alone, up to the charge limit.
     *
     * @param chargeLimitPct the level to charge to, which is also the charge limit to set, never null
     * @param grid the grid charge, never null
     */
    record Grid(Percent chargeLimitPct, TripGridStart grid) implements TripPlan {
    }

    /**
     * No charge before the trip helps, and the battery falls short: only the level it leaves with.
     *
     * @param departureLevelPct the battery level when the trip leaves, never null
     */
    record Leaves(Percent departureLevelPct) implements TripPlan {
    }
}
