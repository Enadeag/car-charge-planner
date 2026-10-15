package io.github.enadeag.carchargeplanner.engine.forecast;

import io.github.enadeag.carchargeplanner.engine.config.ForecastSelection;
import io.github.enadeag.carchargeplanner.engine.config.ProviderId;
import io.github.enadeag.carchargeplanner.engine.secrets.Secrets;
import io.github.enadeag.carchargeplanner.engine.secrets.SecretsProblem;
import io.github.enadeag.carchargeplanner.engine.secrets.SecretsSource;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Which provider to ask, when, and what to do when it cannot answer. The primary provider is asked first, and its last
 * good forecast is used for as long as it is still useful; only when the primary has nothing usable is the fallback
 * provider asked. One request at a time: two at once would both spend the provider's allowance. Blocking: call it
 * away from the user interface's thread.
 */
public final class ForecastService {

    private final Providers providers;
    private final ForecastCache cache;
    private final SecretsSource secrets;
    private final Map<ProviderId, Instant> retryAt = new HashMap<>();

    /**
     * Makes the service.
     *
     * @param providers the providers it may ask, found by the ids the selection names
     * @param cache where each provider's last good forecast and rate limit are kept
     * @param secrets where the providers' secret settings come from; read on every request
     */
    public ForecastService(Providers providers, ForecastCache cache, SecretsSource secrets) {
        this.providers = providers;
        this.cache = cache;
        this.secrets = secrets;
    }

    /** The stored secrets, or none and why: the providers that need no key still work. */
    private record StoredSecrets(Secrets secrets, Optional<String> problem) {
    }

    /**
     * A forecast for the system, from the cache or a provider.
     *
     * @param system the system to forecast; the cache is used only when it was made for the same one
     * @param selection the providers to ask and their settings that are not secret
     * @param now the current moment, by which the cache's age, waits and rate limits are measured
     * @param force the owner asked for a refresh: skips the minimum refresh interval, never a provider's rate limit
     * @return the forecast and where it came from
     * @throws ForecastUnavailable with the reason of each provider asked, when none has a usable forecast
     */
    public synchronized ForecastResult get(SystemDescription system, ForecastSelection selection, Instant now,
            boolean force) throws ForecastUnavailable {
        var stored = storedSecrets();
        var reasons = new ArrayList<String>();
        var order = selection.order();
        for (int index = 0; index < order.size(); index++) {
            var outcome = ask(order.get(index), system, selection, stored.secrets(), now, force);
            if (outcome instanceof Outcome.Usable usable) {
                var info = providers.find(order.get(index)).orElseThrow().info();
                var role = index == 0 ? ProviderRole.PRIMARY : ProviderRole.FALLBACK;
                return result(usable, info, role, role == ProviderRole.FALLBACK ? primaryName(selection) : "");
            }
            reasons.add(((Outcome.Failure) outcome).reason());
        }
        stored.problem().ifPresent(reasons::add);
        throw new ForecastUnavailable(String.join("; ", reasons));
    }

    /**
     * The settings changed: what failed before may work now, so the waits after failures are forgotten. A rate limit
     * stays: that is the provider's word, not ours.
     */
    public synchronized void forgetFailures() {
        retryAt.clear();
    }

    private StoredSecrets storedSecrets() {
        try {
            return new StoredSecrets(secrets.load(), Optional.empty());
        } catch (SecretsProblem e) {
            return new StoredSecrets(Secrets.NONE, Optional.of(e.getMessage()));
        }
    }

    private Outcome ask(ProviderId providerId, SystemDescription system, ForecastSelection selection, Secrets stored,
            Instant now, boolean force) {
        var found = providers.find(providerId);
        if (found.isEmpty()) {
            return new Outcome.Failure("The forecast provider '" + providerId + "' is not known to this version of the app");
        }
        var provider = found.get();
        var plain = selection.settingsOf(providerId);
        var settings = provider.info().settingsFrom(plain, stored.of(providerId));
        // a changed key does not make the cached forecast wrong, so the key is left out of what it depends on
        var key = cache.key(system, provider.info().settingsFrom(plain, Map.of()));
        return new ProviderAttempt(provider, cache, retryAt, now, force).run(system, settings, key);
    }

    private String primaryName(ForecastSelection selection) {
        return providers.find(selection.primary()).map(provider -> provider.info().name()).orElse("");
    }

    private static ForecastResult result(Outcome.Usable usable, ProviderInfo info, ProviderRole role,
            String primaryName) {
        var source = new ForecastSource(info.id(), info.name(), info.attribution(), usable.fetchedAt(),
                usable.freshness(), role, primaryName, usable.cooldownUntil());
        return new ForecastResult(usable.steps(), source);
    }
}
