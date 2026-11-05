package nl.enadeag.carchargeplanner.engine.forecast;

import nl.enadeag.carchargeplanner.engine.config.ProviderId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Where a forecast came from: who made it, the credit their terms ask for, how fresh it is, and which provider asked
 * for it refused its API key, which a forecast that is still usable must not hide.
 *
 * @param providerId the provider that made it
 * @param providerName its name
 * @param attribution the credit its terms ask for
 * @param fetchedAt when it was fetched
 * @param freshness whether it was fetched just now or comes from the cache, and then whether a refresh was due
 * @param providerRole whether the provider that made it is the primary or the fallback
 * @param primaryName the name of the provider that was asked first; empty when it was the primary
 * @param cooldownUntil the provider that made it refuses requests until then; empty when the provider is not cooling
 *     down
 * @param keyRefusedBy the providers that refused their API key when they were asked for this forecast, or while they
 *     wait after that refusal, in the order they were asked: a primary whose fallback answered, or the provider whose
 *     last forecast is used; empty when none did, also when no provider was asked
 */
public record ForecastSource(
        ProviderId providerId,
        String providerName,
        String attribution,
        Instant fetchedAt,
        Freshness freshness,
        ProviderRole providerRole,
        String primaryName,
        Optional<Instant> cooldownUntil,
        List<ProviderId> keyRefusedBy) {

    /** Copies the providers that refused their key. */
    public ForecastSource {
        keyRefusedBy = List.copyOf(keyRefusedBy);
    }
}
