package io.github.enadeag.carchargeplanner.engine.forecast;

import io.github.enadeag.carchargeplanner.engine.config.ForecastSelection;
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
    private final Map<String, Instant> retryAt = new HashMap<>();

    public ForecastService(Providers providers, ForecastCache cache, SecretsSource secrets) {
        this.providers = providers;
        this.cache = cache;
        this.secrets = secrets;
    }

    /** The stored secrets, or none and why: the providers that need no key still work. */
    private record StoredSecrets(Secrets secrets, Optional<String> problem) {
    }

    /**
     * @param force the owner asked for a refresh: skips the minimum refresh interval, never a provider's rate limit
     * @throws ForecastUnavailable with the reason of each provider asked
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
                return result(usable, info, index > 0, index > 0 ? primaryName(selection) : "");
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

    private Outcome ask(String providerId, SystemDescription system, ForecastSelection selection, Secrets stored,
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

    private static ForecastResult result(Outcome.Usable usable, ProviderInfo info, boolean fromFallback,
            String primaryName) {
        var source = new ForecastSource(info.id(), info.name(), info.attribution(), usable.fetchedAt(), usable.fromCache(),
                usable.stale(), fromFallback, primaryName, usable.cooldownUntil());
        return new ForecastResult(usable.steps(), source);
    }
}
