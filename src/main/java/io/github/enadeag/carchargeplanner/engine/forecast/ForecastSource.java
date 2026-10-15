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
 * @param fromCache the network was not used for it
 * @param stale a refresh was due but could not be done
 * @param fromFallback the primary provider had nothing usable
 * @param primaryName the name of the provider that was asked first; empty when it was the primary
 * @param cooldownUntil the provider that made it refuses requests until then
 */
public record ForecastSource(
        String providerId,
        String providerName,
        String attribution,
        Instant fetchedAt,
        boolean fromCache,
        boolean stale,
        boolean fromFallback,
        String primaryName,
        Optional<Instant> cooldownUntil) {
}
