package nl.enadeag.carchargeplanner.engine.secrets;

import java.time.Clock;
import java.time.Duration;

/**
 * Where the API keys are kept: in the system keychain, or in a private file when there is no usable keychain. While
 * the file exists, the keys come from it; {@link #moveFileIntoKeychain()} moves it into a keychain that can be used,
 * and removes it only after every value was written and read back. Loading never moves it. Values never appear in a
 * log line or an error message; where the keys are kept is logged each time it changes.
 *
 * <p>The file is a {@link KeyFallback setting}. With {@link KeyFallback#NONE}, as on Android, the keys are kept only
 * in the keychain, so they never end up unencrypted: the file is never read, written, deleted or moved, a load from a
 * keychain that cannot be used fails as keys that could not be read, a save it cannot take fails, and the storage is
 * {@link KeyStorage#UNAVAILABLE} instead of {@link KeyStorage#FILE}. An index the keychain refuses to give then counts
 * as a damaged one, so a save starts a new index and the owner can enter the keys again; the old entries stay until the
 * same names are written again.
 *
 * <p>The keychain is slow to ask and may not answer at all, while the engine wants the keys for a plan every minute,
 * so what it gave is kept in memory: the keys until the next save, and a keychain that could not be used or read is
 * left alone for {@link #RETRY_AFTER}; the keys then come from the file, which is read again only when it changed. A
 * save always tries the keychain. Loads, moves and saves take turns, so a save never mixes with another of them.
 *
 * <p>A locked keychain, such as a desktop's keychain that was not unlocked at login, is not a missing one: the keys in
 * it are not available yet. A load says so, with {@link SecretsProblem.Kind#LOCKED}, and the keychain is asked again at
 * the next load and the next move, so the unlocking is noticed soon. A save into a locked keychain has the system ask
 * the owner to unlock it, and waits up to {@link #UNLOCK_WAIT} for their answer without holding up the loads. The keys
 * go to the file only when the owner dismisses that prompt or there turns out to be no keychain; when the app cannot
 * have it unlocked, or nobody answers, nothing is saved and the owner is told to unlock it and save again.
 */
public final class ApiKeys implements SecretsSource {

    /** How long a keychain that could not be used, or not be read, is left alone before it is asked again. */
    public static final Duration RETRY_AFTER = Duration.ofMinutes(5);
    /** How long a save waits for the owner to answer the system's prompt to unlock the keychain. */
    public static final Duration UNLOCK_WAIT = Duration.ofMinutes(2);

    private final Keychain keychain;
    private final SecretsFile file;
    private final KeychainMemory memory;
    private final KeychainWithFallback keys;
    private final Object turn = new Object();
    /** Held for a whole save, which may wait for the owner without holding the turn, so saves never mix. */
    private final Object saving = new Object();

    /**
     * Makes the store; nothing is read or written yet. It takes a {@link Clock}, not an {@code InstantSource}, which
     * Android has only from Android 14 on and desugaring does not add (ADR 0010).
     *
     * @param keychain the system's keychain, or {@link NoKeychain} where there is none
     * @param file where the keys go when the keychain cannot be used and the fallback is the file; with
     *     {@link KeyFallback#NONE} it is never read, written, deleted or moved
     * @param fallback where the keys go when the keychain cannot be used: the file, or nowhere; never null
     * @param clock tells when a keychain that failed may be asked again; only its instant is used; never null
     * @throws IllegalArgumentException when the fallback or the clock is null
     */
    public ApiKeys(Keychain keychain, SecretsFile file, KeyFallback fallback, Clock clock) {
        if (fallback == null) {
            throw new IllegalArgumentException("the fallback must not be null");
        }
        if (clock == null) {
            throw new IllegalArgumentException("the clock must not be null");
        }
        this.keychain = keychain;
        this.file = file;
        this.memory = new KeychainMemory(clock, RETRY_AFTER, file);
        this.keys = new KeychainWithFallback(keychain, new FallbackFile(file, fallback), memory, fallback);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Only reads: while the file exists, and is the fallback, the keys come from it, and it is left where it is.
     * Otherwise asks the keychain the first time, after a save, at every load while it is locked, and once
     * {@link #RETRY_AFTER} has passed since it failed; in between answers from memory.
     *
     * @throws SecretsProblem of the kind {@link SecretsProblem.Kind#LOCKED} while the keychain that holds the keys is
     *     locked; of the kind {@link SecretsProblem.Kind#FAILED} when the keychain or the file could not be read, the
     *     file holds a name that is not allowed, or, without the file as fallback, the keychain cannot be used
     */
    @Override
    public Secrets load() throws SecretsProblem {
        synchronized (turn) {
            return keys.load();
        }
    }

    /**
     * Moves the keys from the file into the keychain, when there is a file and the keychain can be used. They are
     * laid over what the keychain holds, so a key kept only in the keychain, such as one the owner did not enter again
     * while it was locked, stays, as does an entry the keychain refuses to read, unless the file holds that key; the
     * file is removed only after every value was written and read back. Does nothing without a file, while the
     * keychain is left alone after a failure, or while it is locked, so the next call notices the unlocking. A
     * keychain that cannot be used, or a move that fails, leaves the keychain alone for {@link #RETRY_AFTER}. A file
     * that cannot be read, or holds a name that is not allowed, stays, and a load says why. May ask the keychain,
     * which can take a few seconds: call it away from the user interface's thread. Without the file as fallback
     * ({@link KeyFallback#NONE}) it does nothing.
     */
    public void moveFileIntoKeychain() {
        synchronized (turn) {
            keys.moveFileIn();
        }
    }

    /**
     * Stores them all, replacing what is stored, in the keychain when it works and else in the file; as
     * {@link #applyChanges(KeyChanges, Runnable)}, which a client that may not have read every stored key uses instead.
     * It reads no entry, so it also replaces the ones the keychain refuses to read, and a keychain that then fails
     * sends the keys to the file, or fails the save without one, as for any other save.
     *
     * @param secrets every provider's secret settings; what is left out is forgotten
     * @throws SecretsProblem as {@link #applyChanges(KeyChanges, Runnable)}
     */
    public void save(Secrets secrets) throws SecretsProblem {
        secrets.checkNames();
        store(KeychainWithFallback.Edit.replacing(secrets), () -> { });
    }

    /**
     * Applies the owner's changes to the stored keys, in the keychain when it works and else in the file, when that is
     * the fallback: every key they did not enter or empty stays as it is, also one that could not be read, such as
     * from a locked keychain or an entry the keychain refuses to read. When the keychain holds such an entry, the keys
     * never go to the file.
     * Tries the keychain also while it is left alone after a failure: the owner asked for this. When the keychain is
     * locked, the system asks the owner to unlock it and this waits up to {@link #UNLOCK_WAIT} for their answer, while
     * loads go on. Blocks: call it away from the user interface's thread.
     *
     * @param changes what the owner entered and emptied
     * @param prompting runs, on this thread, once the system's prompt to unlock the keychain shows, so the client can
     *     tell the owner; not when the keychain unlocks without one
     * @throws SecretsProblem of the kind {@link SecretsProblem.Kind#LOCKED} when the keychain stayed locked, as the
     *     app cannot have it unlocked, nobody answered in time, or {@link #stopUnlocking()} was called: nothing was
     *     saved; without the file as fallback, also when the owner dismissed the prompt. Of the kind
     *     {@link SecretsProblem.Kind#FAILED} when a name has characters that are not allowed, the file could not be
     *     written, the keychain failed while it holds an entry it refuses to read, or, without the file as fallback,
     *     the keychain cannot be used or failed: nothing was saved then
     */
    public void applyChanges(KeyChanges changes, Runnable prompting) throws SecretsProblem {
        changes.entered().checkNames();
        store(KeychainWithFallback.Edit.changing(changes), prompting);
    }

    private void store(KeychainWithFallback.Edit edit, Runnable prompting) throws SecretsProblem {
        synchronized (saving) {
            if (inTurn(edit) != KeychainWithFallback.SaveResult.LOCKED) {
                return;
            }
            var stored = switch (keys.unlock(UNLOCK_WAIT, prompting)) {
                case UNLOCKED -> inTurn(edit);
                case DISMISSED -> {
                    synchronized (turn) {
                        yield keys.dismissed(edit);
                    }
                }
                case NOT_ANSWERED -> KeychainWithFallback.SaveResult.LOCKED;
            };
            if (stored == KeychainWithFallback.SaveResult.LOCKED) {
                throw new SecretsProblem(SecretsProblem.Kind.LOCKED,
                        "The system keychain is locked. Unlock it and save again.");
            }
        }
    }

    private KeychainWithFallback.SaveResult inTurn(KeychainWithFallback.Edit edit) throws SecretsProblem {
        synchronized (turn) {
            return keys.save(edit);
        }
    }

    /**
     * Stops a save that waits for the owner to unlock the keychain, such as when its window closes: it then saves
     * nothing and says that the keychain is locked. Nothing happens when no save waits. Never blocks for long.
     */
    public void stopUnlocking() {
        keychain.stopUnlocking();
    }

    /**
     * Where the keys are kept now. Finds out as {@link #load()} does, so it asks the keychain only when a load would.
     *
     * @return the file while it exists or when the keychain cannot be used, with the file as fallback; unavailable
     *     when the keychain cannot be used without it; else the locked keychain while it holds the keys and is locked,
     *     or the keychain
     */
    public KeyStorage storage() {
        synchronized (turn) {
            try {
                load();
            } catch (SecretsProblem ignored) {
                // where they are kept is known all the same
            }
            return memory.where();
        }
    }

    /**
     * The file the keys go to without a keychain, when it is the fallback.
     *
     * @return the file this was made with, also when it is not the fallback
     */
    public SecretsFile file() {
        return file;
    }
}
