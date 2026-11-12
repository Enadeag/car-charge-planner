package nl.enadeag.carchargeplanner.engine.dashboard;

import java.util.List;

final class ListBuilder {

    private ListBuilder() {
    }

    /**
     * The one-off away periods that have not ended when the plan was made.
     *
     * @param parts the plan's parts, never null
     * @return those of the stored periods that end after the plan was made, by kind, in the stored order
     */
    static List<ComingAway> away(PlanParts parts) {
        var today = parts.today();
        return parts.away().stream()
                .filter(period -> period.end().isAfter(parts.madeAt()))
                .map(period -> ComingAway.describe(period, parts.zone(), today))
                .toList();
    }

    /**
     * Every stored trip, also one under way, one that returned already and one made without this car.
     *
     * @param parts the plan's parts, never null
     * @return one per stored trip, with its days, in the stored order
     */
    static List<ListedTrip> trips(PlanParts parts) {
        return parts.trips().stream().map(trip -> ListedTrip.of(trip, parts.zone(), parts.today())).toList();
    }
}
