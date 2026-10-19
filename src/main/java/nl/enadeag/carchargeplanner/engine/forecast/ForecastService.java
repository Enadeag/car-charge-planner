package nl.enadeag.carchargeplanner.engine.forecast;

import nl.enadeag.carchargeplanner.engine.config.ForecastSelection;
import nl.enadeag.carchargeplanner.engine.config.ProviderId;
import nl.enadeag.carchargeplanner.engine.secrets.Secrets;
import nl.enadeag.carchargeplanner.engine.secrets.SecretsProblem;
import nl.enadeag.carchargeplanner.engine.secrets.SecretsSource;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Supplier;

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
    private final Map<ProviderId, Instant> retryAt = new ConcurrentHashMap<>();
    private final Map<ProviderId, ReentrantLock> asking = new ConcurrentHashMap<>();
    private final ProviderHealth health = new ProviderHealth();

    /**
     * Makes the service.
     *
     * @param providers the providers it may ask, found by the ids the selection names
     * @param cache where each provider's last good forecast and rate limit are kept
     * @param secrets where the providers' secret settings come from; read at most once per request, and only when the
     *     cache cannot answer by itself
     */
    public ForecastService(Providers providers, ForecastCache cache, SecretsSource secrets) {
        this.providers = providers;
        this.cache = cache;
        this.secrets = secrets;
    }

    /** The stored secrets, or none and why: the providers that need no key still work. */
    private record KeysOrProblem(Secrets secrets, Optional<SecretsProblem> problem) {
    }

    /**
     * A forecast for the system, from the cache or a provider.
     *
     * @param system the system to forecast; the cache is used only when it was made for the same one
     * @param selection the providers to ask and their settings that are not secret
     * @param now the current moment, by which the cache's age, waits and rate limits are measured
     * @return the forecast and where it came from
     * @throws ForecastUnavailable with the reason of each provider asked, when none has a usable forecast
     */
    public ForecastResult get(SolarSite system, ForecastSelection selection, Instant now) throws ForecastUnavailable {
        return fetch(system, selection, new Once<>(this::storedKeys), now, false);
    }

    /**
     * A forecast for the system that the owner asked to refresh: as {@link #get(SolarSite, ForecastSelection,
     * Instant)}, but a provider is asked also before its minimum refresh interval has passed, and after a failure
     * without its wait; never during its rate limit.
     *
     * @param system the system to forecast; the cache is used only when it was made for the same one
     * @param selection the providers to ask and their settings that are not secret
     * @param now the current moment, by which the cache's age and rate limits are measured
     * @return the forecast and where it came from
     * @throws ForecastUnavailable with the reason of each provider asked, when none has a usable forecast
     */
    public ForecastResult refresh(SolarSite system, ForecastSelection selection, Instant now)
            throws ForecastUnavailable {
        return fetch(system, selection, new Once<>(this::storedKeys), now, true);
    }

    /**
     * A forecast for a system with secret settings that need not be stored yet, such as the ones set-up shows before
     * it saves; from the cache or a provider, as {@link #get(SolarSite, ForecastSelection, Instant)}.
     *
     * @param system the system to forecast; the cache is used only when it was made for the same one
     * @param selection the providers to ask and their settings that are not secret
     * @param secrets every provider's secret settings to ask with, in place of the stored ones; may be none
     * @param now the current moment, by which the cache's age, waits and rate limits are measured
     * @return the forecast and where it came from
     * @throws ForecastUnavailable with the reason of each provider asked, when none has a usable forecast
     */
    public ForecastResult getWithKeys(SolarSite system, ForecastSelection selection, Secrets secrets,
            Instant now) throws ForecastUnavailable {
        return fetch(system, selection, new Once<>(() -> new KeysOrProblem(secrets, Optional.empty())), now, false);
    }

    private ForecastResult fetch(SolarSite system, ForecastSelection selection, Once<KeysOrProblem> stored,
            Instant now, boolean force) throws ForecastUnavailable {
        var reasons = new ArrayList<String>();
        var order = selection.order();
        for (int index = 0; index < order.size(); index++) {
            var outcome = ask(order.get(index), system, selection, stored, now, force);
            if (outcome instanceof ProviderOutcome.Usable usable) {
                var info = providers.find(order.get(index)).orElseThrow().info();
                var role = index == 0 ? ProviderRole.PRIMARY : ProviderRole.FALLBACK;
                return result(usable, info, role, role == ProviderRole.FALLBACK ? primaryName(selection) : "");
            }
            reasons.add(((ProviderOutcome.Failure) outcome).reason());
        }
        var locked = new boolean[1];
        stored.ifMade(secrets -> secrets.problem().ifPresent(problem -> {
            reasons.add(problem.getMessage());
            locked[0] = problem.kind() == SecretsProblem.Kind.LOCKED;
        }));
        throw new ForecastUnavailable(String.join("; ", reasons), locked[0]);
    }

    /**
     * The settings changed: what failed before may work now, so the waits after failures are forgotten. A rate limit
     * stays: that is the provider's word, not ours. Never waits for a provider that is being asked.
     */
    public void forgetFailures() {
        retryAt.clear();
    }

    private KeysOrProblem storedKeys() {
        try {
            return new KeysOrProblem(secrets.load(), Optional.empty());
        } catch (SecretsProblem e) {
            return new KeysOrProblem(Secrets.NONE, Optional.of(e));
        }
    }

    private ProviderOutcome ask(ProviderId providerId, SolarSite system, ForecastSelection selection,
            Once<KeysOrProblem> stored, Instant now, boolean force) {
        var found = providers.find(providerId);
        if (found.isEmpty()) {
            return new ProviderOutcome.Failure(
                    "The forecast provider '" + providerId + "' is not known to this version of the app");
        }
        var provider = found.get();
        var plain = selection.settingsOf(providerId);
        Supplier<Map<String, String>> settings =
                () -> provider.info().settingsFrom(plain, stored.get().secrets().of(providerId));
        // a changed key does not make the cached forecast wrong, so the key is left out of what it depends on
        var key = cache.key(system, provider.info().settingsFrom(plain, Map.of()));
        var lock = asking.computeIfAbsent(providerId, _ -> new ReentrantLock());
        lock.lock();
        try {
            return new ProviderAttempt(provider, cache, retryAt, health, now, force).forecastOrReason(system, settings,
                    key, () -> stored.get().secrets().values());
        } finally {
            lock.unlock();
        }
    }

    private String primaryName(ForecastSelection selection) {
        return providers.find(selection.primary()).map(provider -> provider.info().name()).orElse("");
    }

    private static ForecastResult result(ProviderOutcome.Usable usable, ProviderInfo info, ProviderRole role,
            String primaryName) {
        var source = new ForecastSource(info.id(), info.name(), info.attribution(), usable.fetchedAt(),
                usable.freshness(), role, primaryName, usable.cooldownUntil());
        return new ForecastResult(usable.steps(), source);
    }

    /** A value made the first time it is asked for, and kept; for one request, on one thread. */
    private static final class Once<T> {

        private final Supplier<T> make;
        /** Null until it is made. */
        private T made;

        Once(Supplier<T> make) {
            this.make = make;
        }

        T get() {
            if (made == null) {
                made = Objects.requireNonNull(make.get());
            }
            return made;
        }

        void ifMade(Consumer<T> action) {
            if (made != null) {
                action.accept(made);
            }
        }
    }
}
