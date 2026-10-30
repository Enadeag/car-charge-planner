package nl.enadeag.carchargeplanner.engine.forecast;

/**
 * No provider gave a forecast and nothing usable is cached; the message says why, per provider, and
 * {@link #keychainLocked()} whether the stored keys were not available because the system keychain is locked.
 */
public class ForecastUnavailable extends Exception {

    private final boolean keychainLocked;

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
        super(message);
        this.keychainLocked = keychainLocked;
    }

    /**
     * Whether the stored keys were not available because the system keychain is locked, so unlocking it may help.
     *
     * @return true when the keychain that holds the keys was locked
     */
    public boolean keychainLocked() {
        return keychainLocked;
    }
}
