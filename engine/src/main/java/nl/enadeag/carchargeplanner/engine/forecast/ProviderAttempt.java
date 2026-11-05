package nl.enadeag.carchargeplanner.engine.forecast;

import nl.enadeag.carchargeplanner.engine.config.ProviderId;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;

/**
 * Asking one provider for a forecast, cache first: within the minimum refresh interval the cache answers, without the
 * secret settings being read; while the provider is in a cool-down, or waiting after a failure, its last forecast is
 * used for as long as it is still useful; otherwise the provider is asked. Or, when only the stored forecast may be
 * used, the cache alone answers.
 */
final class ProviderAttempt {

    /**
     * When a provider that failed may be asked again, and whether it failed because it refused its API key, so a
     * wait after a refused key still says so.
     *
     * @param at the moment from which the provider may be asked again
     * @param keyRefused true when the failure was a refused API key
     */
    record Retry(Instant at, boolean keyRefused) {
    }

    /** A request that never got an answer did not use the provider's allowance, so it is tried again soon. */
    static final Duration RETRY_AFTER_UNAVAILABLE = Duration.ofMinutes(5);

    private static final Logger LOG = LoggerFactory.getLogger(ProviderAttempt.class);
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);

    private final ForecastProvider provider;
    private final ProviderInfo info;
    private final ForecastCache cache;
    private final Map<ProviderId, Retry> retryAt;
    private final ProviderHealth health;
    private final Instant now;
    private final boolean force;
    private final boolean marksFailures;

    /**
     * Prepares one attempt; nothing is read or asked yet.
     *
     * @param provider the provider to ask
     * @param cache where its last good forecast and cool-down are kept; written after an answer
     * @param retryAt per provider, when it may be asked again after a failure and whether it refused its API key;
     *     updated here, so a map that is safe for several threads when other providers are asked at the same time
     * @param health whether each provider gave a forecast when it was last asked; updated here
     * @param now the current moment, by which ages, waits and cool-downs are measured
     * @param force the owner asked for a refresh: skip the minimum refresh interval and the wait after a failure,
     *     never a cool-down
     * @param marksFailures true for a request with the saved settings: it waits after an earlier failure, and keeps
     *     what a failure means for the next attempt (the failed refresh and the cool-down in the provider's cache
     *     file, the wait after it); false for a check of settings
     *     that need not be saved, such as set-up's or Settings', which is asked without that wait, never during a
     *     cool-down, and keeps no failure: the file and the wait are the saved system's
     */
    ProviderAttempt(ForecastProvider provider, ForecastCache cache, Map<ProviderId, Retry> retryAt,
            ProviderHealth health, Instant now, boolean force, boolean marksFailures) {
        this.provider = provider;
        this.info = provider.info();
        this.cache = cache;
        this.retryAt = retryAt;
        this.health = health;
        this.now = now;
        this.force = force;
        this.marksFailures = marksFailures;
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
    ProviderOutcome forecastOrReason(SolarSite system, Supplier<Map<String, String>> settings, JsonNode key,
            Supplier<List<String>> storedSecrets) {
        var cached = cache.forecast(info.id(), key, now);
        var held = new CacheState(cached.filter(this::stillUseful), cooldownUntil());
        if (cached.isPresent() && !force && cached.get().age(now).compareTo(info.minRefresh()) < 0) {
            return held.fromCache(cached.get(), Freshness.CACHED, false);
        }
        var values = settings.get();
        var secrets = Stream.concat(info.secretValues(values).stream(), storedSecrets.get().stream()).toList();
        var missing = info.missingRequired(values);
        if (!missing.isEmpty()) { // nothing is sent without what the provider requires
            markRefreshFailed(); // its stored forecast is not refreshed either
            return afterFailure(String.join(", ", missing) + " is required", secrets, CacheState.NONE, false);
        }
        var waiting = held.cooldownUntil().map(until -> new Retry(until, false)).or(this::waitingAfterFailure);
        return waiting.map(retry -> held.stale(retry.keyRefused()).orElseGet(() -> failure(
                        "not asked again until " + clock(retry.at(), system.timezone()), retry.keyRefused())))
                .orElseGet(() -> fetch(system, values, key, secrets, held));
    }

    /**
     * The stored forecast, without asking the provider or reading a secret setting: the last one while it is still
     * useful, told as stale when the provider failed when it was last asked and no answer followed, as a round that
     * asks would tell it, else as cached.
     *
     * @param key what the forecast depends on, as {@link ForecastCache#key} makes it
     * @return the stored forecast, or the reason there is none; never a refused key, as nothing is asked
     */
    ProviderOutcome storedOrReason(JsonNode key) {
        var held = new CacheState(cache.forecast(info.id(), key, now).filter(this::stillUseful), cooldownUntil());
        var freshness = cache.refreshFailed(info.id()) ? Freshness.STALE : Freshness.CACHED;
        return held.usable().map(forecast -> held.fromCache(forecast, freshness, false))
                .orElseGet(() -> failure("no stored forecast that is still useful", false));
    }

    private boolean stillUseful(ForecastCache.Cached forecast) {
        return forecast.age(now).compareTo(info.maxUsefulAge()) <= 0;
    }

    /** The end of the provider's cool-down while it lasts. */
    private Optional<Instant> cooldownUntil() {
        return cache.cooldownUntil(info.id()).filter(now::isBefore);
    }

    private Optional<Retry> waitingAfterFailure() {
        // a check tries the settings typed in the form at once: the wait is the saved system's
        return force || !marksFailures ? Optional.empty()
                : Optional.ofNullable(retryAt.get(info.id())).filter(retry -> now.isBefore(retry.at()));
    }

    /**
     * Asks the provider, and keeps its forecast or what its failure means for the next attempt.
     *
     * @param system the system to forecast
     * @param settings the provider's settings with values, secret ones included
     * @param key what the forecast depends on, under which an answer is cached
     * @param secrets the secret values to keep out of a reason
     * @param held what the cache held before the provider was asked; it has no cool-down, or the provider would
     *     not be asked
     * @return the provider's forecast, else as {@link #afterFailure} tells it
     */
    private ProviderOutcome fetch(SolarSite system, Map<String, String> settings, JsonNode key,
            List<String> secrets, CacheState held) {
        try {
            var steps = StepRules.requireValid(provider.fetch(system, settings, now));
            retryAt.remove(info.id());
            health.recordAnswer(info);
            cache.saveForecast(info.id(), key, now, steps);
            return new ProviderOutcome.Usable(steps, now, Freshness.FRESH, Optional.empty(), false);
        } catch (ProviderError.RateLimited e) {
            var until = now.plus(e.retryAfter().orElse(info.minRefresh()));
            if (marksFailures) {
                cache.saveCooldown(info.id(), until);
            }
            return afterFailure(e.getMessage(), secrets, held.inCooldownUntil(until), false);
        } catch (ProviderError.Unavailable e) {
            waitUntil(new Retry(now.plus(RETRY_AFTER_UNAVAILABLE), false));
            markRefreshFailed();
            return afterFailure(e.getMessage(), secrets, held, false);
        } catch (ProviderError e) {
            boolean keyRefused = e instanceof ProviderError.KeyRefused;
            waitUntil(new Retry(now.plus(info.minRefresh()), keyRefused)); // it answered: that counted
            markRefreshFailed();
            return afterFailure(e.getMessage(), secrets, held, keyRefused);
        } catch (RuntimeException e) {
            // a bug in a provider must not stop the app; its message may hold a secret, so only its kind is told
            var kind = e.getClass().getSimpleName();
            LOG.error("forecast provider " + info.id() + " failed unexpectedly (" + kind + ")");
            waitUntil(new Retry(now.plus(info.minRefresh()), false));
            markRefreshFailed();
            return afterFailure("an unexpected error (" + kind + ")", secrets, held, false);
        }
    }

    /**
     * Makes the provider wait after a failure, unless this is a check of settings that need not be saved: its wait,
     * like the mark in the cache file, would hold for the saved system's rounds too.
     */
    private void waitUntil(Retry retry) {
        if (marksFailures) {
            retryAt.put(info.id(), retry);
        }
    }

    /**
     * Keeps in the cache file that the provider's forecast could not be refreshed, unless this is a check of settings
     * that need not be saved; a file that says so already is not written again, as a missing setting meets it every
     * minute.
     */
    private void markRefreshFailed() {
        if (marksFailures && !cache.refreshFailed(info.id())) {
            cache.saveRefreshFailed(info.id());
        }
    }

    /**
     * The provider gave no forecast: its last one while that is still useful, else the reason. A server can echo what
     * it was sent, so the reason loses every secret value before it is logged or told.
     *
     * @param given the reason as the failure gave it, which may hold a secret value
     * @param secrets the secret values to keep out of the reason
     * @param held what the cache holds now, the cool-down just told included
     * @param keyRefused true when the provider refused its API key
     * @return the last forecast as a stale one, or the failure with its redacted reason
     */
    private ProviderOutcome afterFailure(String given, List<String> secrets, CacheState held, boolean keyRefused) {
        var reason = ProviderText.redacted(given, secrets);
        health.recordFailure(info, reason);
        return held.stale(keyRefused).orElseGet(() -> failure(reason, keyRefused));
    }

    private ProviderOutcome failure(String reason, boolean keyRefused) {
        return new ProviderOutcome.Failure(info.name() + ": " + reason, keyRefused);
    }

    private static String clock(Instant moment, ZoneId zone) {
        return CLOCK.format(moment.atZone(zone));
    }

    /**
     * What the cache holds for the provider during one attempt, handed from step to step so none of it outlives that
     * attempt.
     *
     * @param usable the last forecast while it is still useful, for when the provider gives none
     * @param cooldownUntil the end of the provider's cool-down, told along with a cached forecast
     */
    private record CacheState(Optional<ForecastCache.Cached> usable, Optional<Instant> cooldownUntil) {

        /** Nothing to fall back on and nothing to tell: no useful forecast, no cool-down. */
        static final CacheState NONE = new CacheState(Optional.empty(), Optional.empty());

        /**
         * The same, now that the provider said it was asked too often.
         *
         * @param until the end of the cool-down
         * @return this state with that cool-down
         */
        CacheState inCooldownUntil(Instant until) {
            return new CacheState(usable, Optional.of(until));
        }

        /**
         * The last forecast, while it is still useful, as a stale one.
         *
         * @param keyRefused true when the provider gave none because it refused its API key
         * @return the stale forecast, or empty when there is no useful one
         */
        Optional<ProviderOutcome> stale(boolean keyRefused) {
            return usable.map(forecast -> fromCache(forecast, Freshness.STALE, keyRefused));
        }

        /**
         * A cached forecast, with the provider's cool-down when it has one.
         *
         * @param forecast the cached forecast to give
         * @param freshness how fresh it is told to be
         * @param keyRefused true when it is given because the provider refused its API key
         * @return the forecast to use
         */
        ProviderOutcome fromCache(ForecastCache.Cached forecast, Freshness freshness, boolean keyRefused) {
            return new ProviderOutcome.Usable(List.copyOf(forecast.steps()), forecast.fetchedAt(), freshness,
                    cooldownUntil, keyRefused);
        }
    }
}
