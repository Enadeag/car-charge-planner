package nl.enadeag.carchargeplanner.engine.forecast;

import nl.enadeag.carchargeplanner.engine.config.ProviderId;
import java.lang.System.Logger.Level;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;
import tools.jackson.databind.JsonNode;

/**
 * Asking one provider for a forecast, cache first: within the minimum refresh interval the cache answers, without the
 * secret settings being read; while the provider is rate limited, or waiting after a failure, its last forecast is
 * used for as long as it is still useful; otherwise the provider is asked.
 */
final class ProviderAttempt {

    /** A request that never got an answer did not use the provider's allowance, so it is tried again soon. */
    static final Duration RETRY_AFTER_UNAVAILABLE = Duration.ofMinutes(5);

    private static final System.Logger LOG = System.getLogger(ProviderAttempt.class.getName());
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);

    private final ForecastProvider provider;
    private final ProviderInfo info;
    private final ForecastCache cache;
    private final Map<ProviderId, Instant> retryAt;
    private final ProviderHealth health;
    private final Instant now;
    private final boolean force;

    /**
     * Prepares one attempt; nothing is read or asked yet.
     *
     * @param provider the provider to ask
     * @param cache where its last good forecast and rate limit are kept; written after an answer
     * @param retryAt per provider, when it may be asked again after a failure; updated here, so a map that is safe
     *     for several threads when other providers are asked at the same time
     * @param health whether each provider gave a forecast when it was last asked; updated here
     * @param now the current moment, by which ages, waits and rate limits are measured
     * @param force the owner asked for a refresh: skip the minimum refresh interval and the wait after a failure,
     *     never a rate limit
     */
    ProviderAttempt(ForecastProvider provider, ForecastCache cache, Map<ProviderId, Instant> retryAt,
            ProviderHealth health, Instant now, boolean force) {
        this.provider = provider;
        this.info = provider.info();
        this.cache = cache;
        this.retryAt = retryAt;
        this.health = health;
        this.now = now;
        this.force = force;
    }

    /**
     * Gets a forecast from the cache or the provider, whichever the rules allow; never throws.
     *
     * @param system the system to forecast
     * @param settings gives the provider's settings with values, secret ones included; asked only when the cache
     *     cannot answer by itself, as it may read the keychain
     * @param key what the forecast depends on, as {@link ForecastCache#key} makes it
     * @param storedSecrets gives every stored secret value of every provider, which a reason never shows; asked right
     *     after {@code settings}, so also only when the cache cannot answer by itself
     * @return a forecast to use, or the reason there is none; it never holds a secret value
     */
    ProviderOutcome forecastOrReason(SystemDescription system, Supplier<Map<String, String>> settings, JsonNode key,
            Supplier<List<String>> storedSecrets) {
        var cached = cache.forecast(info.id(), key, now);
        var held = new CacheState(cached.filter(forecast -> forecast.age(now).compareTo(info.maxUsefulAge()) <= 0),
                cache.cooldownUntil(info.id()).filter(now::isBefore));
        if (cached.isPresent() && !force && cached.get().age(now).compareTo(info.minRefresh()) < 0) {
            return held.fromCache(cached.get(), Freshness.CACHED);
        }
        var values = settings.get();
        var secrets = Stream.concat(info.secretValues(values).stream(), storedSecrets.get().stream()).toList();
        var missing = info.missingRequired(values);
        if (!missing.isEmpty()) { // nothing is sent without what the provider requires
            return afterFailure(String.join(", ", missing) + " is required", secrets, CacheState.NONE);
        }
        return held.cooldownUntil().or(this::waitingAfterFailure)
                .map(until -> held.stale().orElseGet(
                        () -> failure("not asked again until " + clock(until, system.timezone()))))
                .orElseGet(() -> fetch(system, values, key, secrets, held));
    }

    private Optional<Instant> waitingAfterFailure() {
        return force ? Optional.empty() : Optional.ofNullable(retryAt.get(info.id())).filter(now::isBefore);
    }

    /**
     * Asks the provider, and keeps its forecast or what its failure means for the next attempt.
     *
     * @param system the system to forecast
     * @param settings the provider's settings with values, secret ones included
     * @param key what the forecast depends on, under which an answer is cached
     * @param secrets the secret values to keep out of a reason
     * @param held what the cache held before the provider was asked; it has no rate limit, or the provider would
     *     not be asked
     * @return the provider's forecast, else as {@link #afterFailure} tells it
     */
    private ProviderOutcome fetch(SystemDescription system, Map<String, String> settings, JsonNode key,
            List<String> secrets, CacheState held) {
        try {
            var steps = Steps.requireValid(provider.fetch(system, settings, now));
            retryAt.remove(info.id());
            health.recordAnswer(info);
            cache.saveForecast(info.id(), key, now, steps);
            return new ProviderOutcome.Usable(steps, now, Freshness.FRESH, Optional.empty());
        } catch (ProviderError.RateLimited e) {
            var until = now.plus(e.retryAfter().orElse(info.minRefresh()));
            cache.saveCooldown(info.id(), until);
            return afterFailure(e.getMessage(), secrets, held.rateLimitedUntil(until));
        } catch (ProviderError.Unavailable e) {
            retryAt.put(info.id(), now.plus(RETRY_AFTER_UNAVAILABLE));
            return afterFailure(e.getMessage(), secrets, held);
        } catch (ProviderError e) {
            retryAt.put(info.id(), now.plus(info.minRefresh())); // it answered: that request counted
            return afterFailure(e.getMessage(), secrets, held);
        } catch (RuntimeException e) {
            // a bug in a provider must not stop the app; its message may hold a secret, so only its kind is told
            var kind = e.getClass().getSimpleName();
            LOG.log(Level.ERROR, "forecast provider " + info.id() + " failed unexpectedly (" + kind + ")");
            retryAt.put(info.id(), now.plus(info.minRefresh()));
            return afterFailure("an unexpected error (" + kind + ")", secrets, held);
        }
    }

    /**
     * The provider gave no forecast: its last one while that is still useful, else the reason. A server can echo what
     * it was sent, so the reason loses every secret value before it is logged or told.
     *
     * @param given the reason as the failure gave it, which may hold a secret value
     * @param secrets the secret values to keep out of the reason
     * @param held what the cache holds now, the rate limit just told included
     * @return the last forecast as a stale one, or the failure with its redacted reason
     */
    private ProviderOutcome afterFailure(String given, List<String> secrets, CacheState held) {
        var reason = ProviderText.redacted(given, secrets);
        health.recordFailure(info, reason);
        return held.stale().orElseGet(() -> failure(reason));
    }

    private ProviderOutcome failure(String reason) {
        return new ProviderOutcome.Failure(info.name() + ": " + reason);
    }

    private static String clock(Instant moment, ZoneId zone) {
        return CLOCK.format(moment.atZone(zone));
    }

    /**
     * What the cache holds for the provider during one attempt, handed from step to step so none of it outlives that
     * attempt.
     *
     * @param usable the last forecast while it is still useful, for when the provider gives none
     * @param cooldownUntil the end of the provider's rate limit, told along with a cached forecast
     */
    private record CacheState(Optional<ForecastCache.Cached> usable, Optional<Instant> cooldownUntil) {

        /** Nothing to fall back on and nothing to tell: no useful forecast, no rate limit. */
        static final CacheState NONE = new CacheState(Optional.empty(), Optional.empty());

        /**
         * The same, now that the provider said it is rate limited.
         *
         * @param until the end of the rate limit
         * @return this state with that rate limit
         */
        CacheState rateLimitedUntil(Instant until) {
            return new CacheState(usable, Optional.of(until));
        }

        /**
         * The last forecast, while it is still useful, as a stale one.
         *
         * @return the stale forecast, or empty when there is no useful one
         */
        Optional<ProviderOutcome> stale() {
            return usable.map(forecast -> fromCache(forecast, Freshness.STALE));
        }

        /**
         * A cached forecast, with the provider's rate limit when it has one.
         *
         * @param forecast the cached forecast to give
         * @param freshness how fresh it is told to be
         * @return the forecast to use
         */
        ProviderOutcome fromCache(ForecastCache.Cached forecast, Freshness freshness) {
            return new ProviderOutcome.Usable(List.copyOf(forecast.steps()), forecast.fetchedAt(), freshness,
                    cooldownUntil);
        }
    }
}
