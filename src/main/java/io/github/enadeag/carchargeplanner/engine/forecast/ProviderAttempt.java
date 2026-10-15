package io.github.enadeag.carchargeplanner.engine.forecast;

import java.lang.System.Logger.Level;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import tools.jackson.databind.JsonNode;

/**
 * Asking one provider for a forecast, cache first: within the minimum refresh interval the cache answers; while the
 * provider is rate limited, or waiting after a failure, its last forecast is used for as long as it is still useful;
 * otherwise the provider is asked.
 */
final class ProviderAttempt {

    /** A request that never got an answer did not use the provider's allowance, so it is tried again soon. */
    static final Duration RETRY_AFTER_UNAVAILABLE = Duration.ofMinutes(5);

    private static final System.Logger LOG = System.getLogger(ProviderAttempt.class.getName());
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");

    private final ForecastProvider provider;
    private final ProviderInfo info;
    private final ForecastCache cache;
    private final Map<String, Instant> retryAt;
    private final Instant now;
    private final boolean force;

    /**
     * @param retryAt per provider id, when it may be asked again after a failure; updated here
     * @param force the owner asked for a refresh: skip the minimum refresh interval and the wait after a failure,
     *     never a rate limit
     */
    ProviderAttempt(ForecastProvider provider, ForecastCache cache, Map<String, Instant> retryAt, Instant now,
            boolean force) {
        this.provider = provider;
        this.info = provider.info();
        this.cache = cache;
        this.retryAt = retryAt;
        this.now = now;
        this.force = force;
    }

    Outcome run(SystemDescription system, Map<String, String> settings, JsonNode key) {
        var missing = info.missingRequired(settings);
        if (!missing.isEmpty()) { // nothing is sent without what the provider requires
            return failure(String.join(", ", missing) + " is required");
        }
        var cached = cache.forecast(info.id(), key, now);
        var cooldown = cache.cooldownUntil(info.id()).filter(now::isBefore);
        var usable = cached.filter(forecast -> forecast.age(now).compareTo(info.maxUsefulAge()) <= 0);
        if (cached.isPresent() && !force && cached.get().age(now).compareTo(info.minRefresh()) < 0) {
            return fromCache(cached.get(), false, cooldown);
        }
        var waitUntil = cooldown.or(this::waitingAfterFailure);
        if (waitUntil.isPresent()) {
            return usable.map(forecast -> fromCache(forecast, true, cooldown))
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
            cache.saveForecast(info.id(), key, now, steps);
            return new Outcome.Usable(steps, now, false, false, Optional.empty());
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

    private Outcome afterFailure(String reason, Optional<ForecastCache.Cached> usable, Optional<Instant> cooldown) {
        return usable.map(forecast -> fromCache(forecast, true, cooldown)).orElseGet(() -> failure(reason));
    }

    private static Outcome fromCache(ForecastCache.Cached forecast, boolean stale, Optional<Instant> cooldown) {
        return new Outcome.Usable(List.copyOf(forecast.steps()), forecast.fetchedAt(), true, stale, cooldown);
    }

    private Outcome failure(String reason) {
        return new Outcome.Failure(info.name() + ": " + reason);
    }

    private static String clock(Instant moment, ZoneId zone) {
        return CLOCK.format(moment.atZone(zone));
    }
}
