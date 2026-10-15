package io.github.enadeag.carchargeplanner.engine.forecast;

import java.time.Instant;
import java.util.Optional;

/**
 * Where a forecast came from, for the dashboard's footer: who made it, the credit their terms ask for, and how fresh
 * it is.
 *
 * @param fromCache the network was not used for it
 * @param stale a refresh was due but could not be done
 * @param fromFallback the primary provider had nothing usable
 * @param primaryName who was asked first, for "from the fallback, the primary unavailable"; empty when it was the primary
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
