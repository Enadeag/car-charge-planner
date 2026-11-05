package nl.enadeag.carchargeplanner.engine.forecast;

import nl.enadeag.carchargeplanner.engine.config.ProviderId;
import java.util.List;

/**
 * No provider gave a forecast and nothing usable is cached; the message says why, per provider,
 * {@link #keychainLocked()} whether the stored keys were not available because the system keychain is locked, and
 * {@link #keyRefusedBy()} which providers refused their API key.
 */
public class ForecastUnavailable extends Exception {

    private final boolean keychainLocked;
    private final List<ProviderId> keyRefusedBy;

    /**
     * Makes the exception, for when the stored keys were not the problem.
     *
     * @param message the reason of each provider asked, in words for the owner
     */
    public ForecastUnavailable(String message) {
        this(message, false);
    }

    /**
     * Makes the exception.
     *
     * @param message the reason of each provider asked, in words for the owner
     * @param keychainLocked true when a provider was asked without the stored keys because the system keychain that
     *     holds them is locked
     */
    public ForecastUnavailable(String message, boolean keychainLocked) {
        this(message, keychainLocked, List.of());
    }

    /**
     * Makes the exception.
     *
     * @param message the reason of each provider asked, in words for the owner
     * @param keychainLocked true when a provider was asked without the stored keys because the system keychain that
     *     holds them is locked
     * @param keyRefusedBy the providers that refused their API key, in the order they were asked; empty when none did
     */
    public ForecastUnavailable(String message, boolean keychainLocked, List<ProviderId> keyRefusedBy) {
        super(message);
        this.keychainLocked = keychainLocked;
        this.keyRefusedBy = List.copyOf(keyRefusedBy);
    }

    /**
     * Whether the stored keys were not available because the system keychain is locked, so unlocking it may help.
     *
     * @return true when the keychain that holds the keys was locked
     */
    public boolean keychainLocked() {
        return keychainLocked;
    }

    /**
     * The providers that refused their API key, so the owner can be told that only a new key helps, apart from the
     * failures that pass by themselves.
     *
     * @return the providers in the order they were asked; empty when none refused its key
     */
    public List<ProviderId> keyRefusedBy() {
        return keyRefusedBy;
    }
}
