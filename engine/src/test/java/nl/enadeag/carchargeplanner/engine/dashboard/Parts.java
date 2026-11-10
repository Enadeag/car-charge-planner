package nl.enadeag.carchargeplanner.engine.dashboard;

import nl.enadeag.carchargeplanner.engine.config.Percent;
import nl.enadeag.carchargeplanner.engine.config.ProviderId;
import nl.enadeag.carchargeplanner.engine.forecast.ForecastSource;
import nl.enadeag.carchargeplanner.engine.forecast.Freshness;
import nl.enadeag.carchargeplanner.engine.forecast.ProviderRole;
import nl.enadeag.carchargeplanner.engine.plan.DayPlan;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

/** A plan's parts for the tests that need only its days: an entered level, no advice, no away periods and no trips. */
final class Parts {

    private Parts() {
    }

    static PlanParts of(Instant madeAt, ZoneId zone, List<DayPlan> days, double baseloadKw) {
        return new PlanParts(madeAt, zone, days, baseloadKw, new Percent(50), false, Optional.empty(), List.of(),
                new Percent(20), new Percent(80), List.of(), List.of(), source(madeAt));
    }

    /** A fresh forecast of the primary provider, fetched at a moment, which no provider refused a key for. */
    static ForecastSource source(Instant fetchedAt) {
        return new ForecastSource(new ProviderId("main"), "Main", "Forecast by Main", fetchedAt, Freshness.FRESH,
                ProviderRole.PRIMARY, "", Optional.empty(), List.of());
    }

    /** The footer of {@link #source(Instant)}, for a plan made when it was fetched. */
    static Footer footer(Instant fetchedAt) {
        return Footer.of(source(fetchedAt), fetchedAt);
    }
}
