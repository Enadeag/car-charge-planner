package nl.enadeag.carchargeplanner.engine.dashboard;

import nl.enadeag.carchargeplanner.engine.config.ProviderId;
import nl.enadeag.carchargeplanner.engine.forecast.ForecastSource;
import nl.enadeag.carchargeplanner.engine.forecast.Freshness;
import nl.enadeag.carchargeplanner.engine.forecast.ProviderRole;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * The forecast behind a plan: the provider's credit, which provider stood in for which, when the forecast was fetched
 * and whether that is stale, until when the provider is busy, and which providers refused their API key.
 *
 * @param credit the credit the provider's terms ask for, as the provider gives it, never null
 * @param fallback whether the fallback provider gave the forecast, and for which primary provider, never null
 * @param fetchedAt when the forecast was fetched, never null
 * @param stale true when a refresh was due but failed, so the plan uses this older forecast
 * @param busyUntil until when the provider refuses requests; present only while that is after the moment the plan
 *     was made
 * @param keyRefusedBy the providers that refused their API key, in the order they were asked, also while another
 *     forecast is used, never null; empty when none did
 */
public record Footer(String credit, Fallback fallback, Instant fetchedAt, boolean stale, Optional<Instant> busyUntil,
        List<ProviderId> keyRefusedBy) {

    /** Keeps a copy of the providers that refused their key. */
    public Footer {
        keyRefusedBy = List.copyOf(keyRefusedBy);
    }

    /**
     * The footer of a plan, also of one without days.
     *
     * @param source where the plan's forecast came from, never null
     * @param madeAt when the plan was made, never null; a cool-down that ends at or before it is over
     * @return the footer, busy only while the provider's cool-down lasts past {@code madeAt}
     */
    public static Footer of(ForecastSource source, Instant madeAt) {
        Fallback fallback;
        if (source.providerRole() != ProviderRole.FALLBACK) {
            fallback = new Fallback.None();
        } else if (source.primaryName().isEmpty()) {
            fallback = new Fallback.ForUnknown();
        } else {
            fallback = new Fallback.For(source.primaryName());
        }
        return new Footer(source.attribution(), fallback, source.fetchedAt(), source.freshness() == Freshness.STALE,
                source.cooldownUntil().filter(until -> until.isAfter(madeAt)), source.keyRefusedBy());
    }

    /** Whether the fallback provider gave the forecast, and for which primary provider. */
    public sealed interface Fallback {

        /** The primary provider gave it. */
        record None() implements Fallback {
        }

        /**
         * The fallback provider gave it, as the primary provider had none.
         *
         * @param primaryName the primary provider's name, never null and not empty
         */
        record For(String primaryName) implements Fallback {

            /**
             * Checks that the primary provider has a name.
             *
             * @throws IllegalArgumentException when the name is empty, which is {@link ForUnknown}
             */
            public For {
                if (primaryName.isEmpty()) {
                    throw new IllegalArgumentException("a primary provider without a name is unknown");
                }
            }
        }

        /** The fallback provider gave it, for a primary provider whose name is not known. */
        record ForUnknown() implements Fallback {
        }
    }
}
