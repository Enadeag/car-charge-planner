package nl.enadeag.carchargeplanner.engine.secrets;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * What {@link ApiKeys} remembers between tries of the keychain: the keys it gave or took last, until when it is left
 * alone after it failed, why it could not be read, and where the keys were kept, which is logged each time it changes.
 * Not safe for threads: whoever holds {@code ApiKeys}' turn uses it.
 */
final class KeychainMemory {

    private static final Logger LOG = LoggerFactory.getLogger(ApiKeys.class);

    private final Clock clock;
    private final Duration retryAfter;
    private final SecretsFile file;
    /** The keys as the keychain gave or took them last; null when they must be read. */
    private Secrets keys;
    /** Until when the keychain is left alone after it could not be used or read; null when it may be asked. */
    private Instant askAgainAt;
    /** Why the keychain could not be read, while it is left alone; null when it could not be used at all instead. */
    private SecretsProblem unreadable;
    /** Where the keys were kept when that was found last; null until it is first known. */
    private KeyStorage where;

    /**
     * @param retryAfter how long a keychain that failed is left alone
     * @param file the file the keys are kept in without a keychain, named in the log
     */
    KeychainMemory(Clock clock, Duration retryAfter, SecretsFile file) {
        this.clock = clock;
        this.retryAfter = retryAfter;
        this.file = file;
    }

    /** @return the keys the keychain gave or took last; null when they must be read */
    Secrets keys() {
        return keys;
    }

    /** @param keys what the keychain holds now; null when it must be read again */
    void remember(Secrets keys) {
        this.keys = keys;
    }

    /** Forgets the keys and every failure, so the keychain is asked at once, as before a save. */
    void forget() {
        keys = null;
        askAgainAt = null;
        unreadable = null;
    }

    boolean waitingToAsk() {
        return askAgainAt != null && clock.instant().isBefore(askAgainAt);
    }

    void waitBeforeAsking() {
        askAgainAt = clock.instant().plus(retryAfter);
    }

    /** @return why the keychain could not be read, while it is left alone; null otherwise */
    SecretsProblem unreadable() {
        return unreadable;
    }

    void unreadable(SecretsProblem problem) {
        unreadable = problem;
    }

    /** @return where the keys were kept when that was found last; null until it is first known */
    KeyStorage where() {
        return where;
    }

    /**
     * Notes where the keys are kept, and logs it when that changed: the file and a locked keychain are warnings, the
     * keychain is normal.
     */
    void keptIn(KeyStorage storage, String why) {
        if (where == storage) {
            return;
        }
        switch (storage) {
            case FILE -> LOG.warn("API keys are kept in " + file.path() + ", readable only by you: "
                    + why);
            case LOCKED_KEYCHAIN -> LOG.warn("API keys are kept in the system keychain, which is locked: "
                    + "they can be read once it is unlocked");
            case KEYCHAIN -> LOG.info("API keys are kept in the system keychain"
                    + (where != null ? " again" : ""));
            case UNAVAILABLE -> LOG.warn("API keys can be neither read nor saved, as there is no file to keep them in: "
                    + why);
        }
        where = storage;
    }
}
