package nl.enadeag.carchargeplanner.engine.forecast;

import nl.enadeag.carchargeplanner.engine.config.ForecastSelection;
import nl.enadeag.carchargeplanner.engine.config.ProviderId;
import nl.enadeag.carchargeplanner.engine.secrets.Secrets;
import nl.enadeag.carchargeplanner.engine.secrets.SecretsProblem;
import nl.enadeag.carchargeplanner.engine.secrets.SecretsSource;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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
    private final Map<ProviderId, ProviderAttempt.Retry> retryAt = new ConcurrentHashMap<>();
    private final Map<ProviderId, ReentrantLock> asking = new ConcurrentHashMap<>();
    private final ProviderHealth health = new ProviderHealth();

    /**
     * Makes the service.
     *
     * @param providers the providers it may ask, found by the ids the selection names
     * @param cache where each provider's last good forecast and cool-down are kept
     * @param secrets where the providers' secret settings come from; read at most once per request, and only when the
     *     cache cannot answer by itself
     */
    public ForecastService(Providers providers, ForecastCache cache, SecretsSource secrets) {
        this.providers = providers;
        this.cache = cache;
        this.secrets = secrets;
    }

    private enum Asking {
        USUAL,
        /**
         * As usual, for a check of settings that need not be saved: a provider is asked without the wait after an
         * earlier failure, never during its cool-down, and what a failure means for the next request is not kept,
         * neither in the provider's cache file nor as its wait, which are the saved system's.
         */
        CHECK,
        /** As usual, but a provider is asked before its minimum refresh interval and without its wait. */
        FORCED,
        /** From the cache only; no provider is asked and no key is read. */
        STORED_ONLY
    }

    /** The stored secrets, or none and why: the providers that need no key still work. */
    private record KeysOrProblem(Secrets secrets, Optional<SecretsProblem> problem) {
    }

    /**
     * A forecast for the system, from the cache or a provider.
     *
     * @param system the system to forecast; the cache is used only when it was made for the same one
     * @param selection the providers to ask and their settings that are not secret
     * @param now the current moment, by which the cache's age, waits and cool-downs are measured
     * @return the forecast and where it came from
     * @throws ForecastUnavailable with the reason of each provider asked, when none has a usable forecast
     */
    public ForecastResult get(SolarSite system, ForecastSelection selection, Instant now) throws ForecastUnavailable {
        return fetch(system, selection, new Once<>(this::storedKeys), now, Asking.USUAL);
    }

    /**
     * The forecast that is stored for the system, without asking any provider or reading a key, such as for a moment
     * the device woke for, where the network is not to be used. The primary provider's stored forecast while it is
     * still useful, else the fallback provider's; it is told as stale when that provider failed when it was last
     * asked, also by another process, and no answer followed, else as cached. It never says a key was refused, as no
     * provider was asked.
     *
     * @param system the system to forecast; a stored forecast is used only when it was made for the same one
     * @param selection the providers whose stored forecasts to use and their settings that are not secret
     * @param now the current moment, by which the stored forecasts' age and cool-downs are measured
     * @return the stored forecast and where it came from
     * @throws ForecastUnavailable with the reason of each provider, when none has a stored forecast that is still
     *     useful
     */
    public ForecastResult stored(SolarSite system, ForecastSelection selection, Instant now)
            throws ForecastUnavailable {
        return fetch(system, selection, new Once<>(this::storedKeys), now, Asking.STORED_ONLY);
    }

    /**
     * A forecast for the system that the owner asked to refresh: as {@link #get(SolarSite, ForecastSelection,
     * Instant)}, but a provider is asked also before its minimum refresh interval has passed, and after a failure
     * without its wait; never during its cool-down.
     *
     * <p>Not wired to a caller yet, and it must not be before this is fixed: when the provider refuses its API key to
     * a forced request within the minimum refresh interval of its last answer, the next ordinary request within that
     * interval names no provider that refused its key, as the cache answers it before the wait after the refusal is
     * looked at.
     *
     * @param system the system to forecast; the cache is used only when it was made for the same one
     * @param selection the providers to ask and their settings that are not secret
     * @param now the current moment, by which the cache's age and cool-downs are measured
     * @return the forecast and where it came from
     * @throws ForecastUnavailable with the reason of each provider asked, when none has a usable forecast
     */
    public ForecastResult refresh(SolarSite system, ForecastSelection selection, Instant now)
            throws ForecastUnavailable {
        return fetch(system, selection, new Once<>(this::storedKeys), now, Asking.FORCED);
    }

    /**
     * A forecast for a system with secret settings that need not be stored yet, such as the ones set-up shows before
     * it saves; from the cache or a provider, as {@link #get(SolarSite, ForecastSelection, Instant)}. A provider's
     * forecast is cached, but its failure is kept neither in the cache file, as a failed refresh or a cool-down, nor
     * as a wait before the provider is asked again: both are one per provider, so a wrong key typed in a form, or
     * another system, would otherwise mark the saved system's forecast as stale, or make its next request wait and
     * say that the key was refused. For the same reason the saved system's wait after an earlier failure does not
     * hold for it: a key typed anew is tried at once. A cool-down holds, as it
     * is the provider's word.
     *
     * @param system the system to forecast; the cache is used only when it was made for the same one
     * @param selection the providers to ask and their settings that are not secret
     * @param secrets every provider's secret settings to ask with, in place of the stored ones; may be none
     * @param now the current moment, by which the cache's age, waits and cool-downs are measured
     * @return the forecast and where it came from
     * @throws ForecastUnavailable with the reason of each provider asked, when none has a usable forecast
     */
    public ForecastResult getWithKeys(SolarSite system, ForecastSelection selection, Secrets secrets,
            Instant now) throws ForecastUnavailable {
        return fetch(system, selection, new Once<>(() -> new KeysOrProblem(secrets, Optional.empty())), now,
                Asking.CHECK);
    }

    private ForecastResult fetch(SolarSite system, ForecastSelection selection, Once<KeysOrProblem> storedKeys,
            Instant now, Asking mode) throws ForecastUnavailable {
        var reasons = new ArrayList<String>();
        var keyRefusedBy = new ArrayList<ProviderId>();
        var order = selection.order();
        for (int index = 0; index < order.size(); index++) {
            var outcome = ask(order.get(index), system, selection, storedKeys, now, mode);
            if (outcome instanceof ProviderOutcome.Usable usable) {
                if (usable.keyRefused()) {
                    keyRefusedBy.add(order.get(index));
                }
                var info = providers.find(order.get(index)).orElseThrow().info();
                var role = index == 0 ? ProviderRole.PRIMARY : ProviderRole.FALLBACK;
                return result(usable, info, role, role == ProviderRole.FALLBACK ? primaryName(selection) : "",
                        keyRefusedBy);
            }
            var failure = (ProviderOutcome.Failure) outcome;
            reasons.add(failure.reason());
            if (failure.keyRefused()) {
                keyRefusedBy.add(order.get(index));
            }
        }
        var locked = new boolean[1];
        storedKeys.ifMade(secrets -> secrets.problem().ifPresent(problem -> {
            reasons.add(problem.getMessage());
            locked[0] = problem.kind() == SecretsProblem.Kind.LOCKED;
        }));
        throw new ForecastUnavailable(String.join("; ", reasons), locked[0], keyRefusedBy);
    }

    /**
     * The settings changed: what failed before may work now, so the waits after failures are forgotten. A cool-down
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
            Once<KeysOrProblem> storedKeys, Instant now, Asking mode) {
        var found = providers.find(providerId);
        if (found.isEmpty()) {
            return new ProviderOutcome.Failure(
                    "The forecast provider '" + providerId + "' is not known to this version of the app");
        }
        var provider = found.get();
        var plain = selection.settingsOf(providerId);
        Supplier<Map<String, String>> settings =
                () -> provider.info().settingsFrom(plain, storedKeys.get().secrets().of(providerId));
        // a changed key does not make the cached forecast wrong, so the key is left out of what it depends on
        var key = cache.key(system, provider.info().settingsFrom(plain, Map.of()));
        var attempt = new ProviderAttempt(provider, cache, retryAt, health, now, mode == Asking.FORCED,
                mode != Asking.CHECK);
        if (mode == Asking.STORED_ONLY) { // asks nobody, so it never waits for a request of another round
            return attempt.storedOrReason(key);
        }
        var lock = asking.computeIfAbsent(providerId, ignored -> new ReentrantLock());
        lock.lock();
        try {
            return attempt.forecastOrReason(system, settings, key, () -> storedKeys.get().secrets().values());
        } finally {
            lock.unlock();
        }
    }

    private String primaryName(ForecastSelection selection) {
        return providers.find(selection.primary()).map(provider -> provider.info().name()).orElse("");
    }

    private static ForecastResult result(ProviderOutcome.Usable usable, ProviderInfo info, ProviderRole role,
            String primaryName, List<ProviderId> keyRefusedBy) {
        var source = new ForecastSource(info.id(), info.name(), info.attribution(), usable.fetchedAt(),
                usable.freshness(), role, primaryName, usable.cooldownUntil(), keyRefusedBy);
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
