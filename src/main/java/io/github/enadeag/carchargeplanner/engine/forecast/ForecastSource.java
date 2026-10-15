package io.github.enadeag.carchargeplanner.engine.forecast;

import java.time.Instant;
import java.util.Optional;

/**
 * Where a forecast came from: who made it, the credit their terms ask for, and how fresh it is.
 *
 * @param providerId the id of the provider that made it
 * @param providerName its name
 * @param attribution the credit its terms ask for
 * @param fetchedAt when it was fetched
 * @param freshness whether it was fetched just now or comes from the cache, and then whether a refresh was due
 * @param providerRole whether the provider that made it is the primary or the fallback
 * @param primaryName the name of the provider that was asked first; empty when it was the primary
 * @param cooldownUntil the provider that made it refuses requests until then
 */
public record ForecastSource(
        String providerId,
        String providerName,
        String attribution,
        Instant fetchedAt,
        Freshness freshness,
        ProviderRole providerRole,
        String primaryName,
        Optional<Instant> cooldownUntil) {
}
