package io.github.enadeag.carchargeplanner.engine.forecast;

import io.github.enadeag.carchargeplanner.engine.config.ProviderId;
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
    /** The secret values to keep out of a reason; known once the settings are read. */
    private List<String> secrets = List.of();

    /**
     * Prepares one attempt; nothing is read or asked yet.
     *
     * @param provider the provider to ask
     * @param cache where its last good forecast and rate limit are kept; written after an answer
     * @param retryAt per provider, when it may be asked again after a failure; updated here, so a map that is safe
     *     for several threads when other providers are asked at the same time
     * @param health whether each provider gave a forecast when it was last asked; updated here
     * @param force the owner asked for a refresh: skip the minimum refresh interval and the wait after a failure,
     *     never a rate limit
     * @param now the current moment, by which ages, waits and rate limits are measured
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
     * @param secretSettings gives the provider's settings with values, secret ones included; asked only when the
     *     cache cannot answer by itself, as it may read the keychain
     * @param key what the forecast depends on, as {@link ForecastCache#key} makes it
     * @param storedSecrets gives every stored secret value of every provider, which a reason never shows; asked only
     *     when the provider is asked, right after {@code secretSettings}
     * @return a forecast to use, or the reason there is none; it never holds a secret value
     */
    Outcome run(SystemDescription system, Supplier<Map<String, String>> secretSettings, JsonNode key,
            Supplier<List<String>> storedSecrets) {
        var cached = cache.forecast(info.id(), key, now);
        var cooldown = cache.cooldownUntil(info.id()).filter(now::isBefore);
        if (cached.isPresent() && !force && cached.get().age(now).compareTo(info.minRefresh()) < 0) {
            return fromCache(cached.get(), Freshness.CACHED, cooldown);
        }
        var settings = secretSettings.get();
        secrets = Stream.concat(info.secretValues(settings).stream(), storedSecrets.get().stream()).toList();
        var missing = info.missingRequired(settings);
        if (!missing.isEmpty()) { // nothing is sent without what the provider requires
            return afterFailure(String.join(", ", missing) + " is required", Optional.empty(), Optional.empty());
        }
        var usable = cached.filter(forecast -> forecast.age(now).compareTo(info.maxUsefulAge()) <= 0);
        var waitUntil = cooldown.or(this::waitingAfterFailure);
        if (waitUntil.isPresent()) {
            return usable.map(forecast -> fromCache(forecast, Freshness.STALE, cooldown))
                    .orElseGet(() -> failure("not asked again until " + clock(waitUntil.get(), system.timezone())));
        }
        return fetch(system, settings, key, usable);
    }

    private Optional<Instant> waitingAfterFailure() {
        return force ? Optional.empty() : Optional.ofNullable(retryAt.get(info.id())).filter(now::isBefore);
    }

    private Outcome fetch(SystemDescription system, Map<String, String> settings, JsonNode key,
            Optional<ForecastCache.Cached> usable) {
        try {
            var steps = Steps.requireValid(provider.fetch(system, settings, now));
            retryAt.remove(info.id());
            health.answered(info);
            cache.saveForecast(info.id(), key, now, steps);
            return new Outcome.Usable(steps, now, Freshness.FRESH, Optional.empty());
        } catch (ProviderError.RateLimited e) {
            var until = now.plus(e.retryAfter().orElse(info.minRefresh()));
            cache.saveCooldown(info.id(), until);
            return afterFailure(e.getMessage(), usable, Optional.of(until));
        } catch (ProviderError.Unavailable e) {
            retryAt.put(info.id(), now.plus(RETRY_AFTER_UNAVAILABLE));
            return afterFailure(e.getMessage(), usable, Optional.empty());
        } catch (ProviderError e) {
            retryAt.put(info.id(), now.plus(info.minRefresh())); // it answered: that request counted
            return afterFailure(e.getMessage(), usable, Optional.empty());
        } catch (RuntimeException e) {
            // a bug in a provider must not stop the app; its message may hold a secret, so only its kind is told
            var kind = e.getClass().getSimpleName();
            LOG.log(Level.ERROR, "forecast provider " + info.id() + " failed unexpectedly (" + kind + ")");
            retryAt.put(info.id(), now.plus(info.minRefresh()));
            return afterFailure("an unexpected error (" + kind + ")", usable, Optional.empty());
        }
    }

    /**
     * The provider gave no forecast: its last one while that is still useful, else the reason. A server can echo what
     * it was sent, so the reason loses every secret value before it is logged or told.
     */
    private Outcome afterFailure(String given, Optional<ForecastCache.Cached> usable, Optional<Instant> cooldown) {
        var reason = ProviderText.redacted(given, secrets);
        health.failed(info, reason);
        return usable.map(forecast -> fromCache(forecast, Freshness.STALE, cooldown)).orElseGet(() -> failure(reason));
    }

    private static Outcome fromCache(ForecastCache.Cached forecast, Freshness freshness, Optional<Instant> cooldown) {
        return new Outcome.Usable(List.copyOf(forecast.steps()), forecast.fetchedAt(), freshness, cooldown);
    }

    private Outcome failure(String reason) {
        return new Outcome.Failure(info.name() + ": " + reason);
    }

    private static String clock(Instant moment, ZoneId zone) {
        return CLOCK.format(moment.atZone(zone));
    }
}
