package nl.enadeag.carchargeplanner.engine.dashboard;

import nl.enadeag.carchargeplanner.engine.config.Percent;
import nl.enadeag.carchargeplanner.engine.plan.DayPlan;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

/** A plan's parts for the tests that need only its days: an entered level, no advice and no trips. */
final class Parts {

    private Parts() {
    }

    static PlanParts of(Instant madeAt, ZoneId zone, List<DayPlan> days, double baseloadKw) {
        return new PlanParts(madeAt, zone, days, baseloadKw, new Percent(50), false, Optional.empty(), List.of(),
                new Percent(20), new Percent(80), List.of());
    }
}
