package nl.enadeag.carchargeplanner.engine.secrets;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A keychain in memory that can be locked, as a desktop keyring can, and whose owner answers the unlock prompt as the
 * test says. Without an answer it waits, as the real prompt does, until {@link #stopUnlocking()} or the wait is over.
 */
public class LockableKeychain extends MemoryKeychain {

    /** While true, every read, write and delete fails as locked. */
    public volatile boolean locked;
    /** What the owner answers at the prompt; empty to leave it unanswered. */
    public volatile Optional<UnlockOutcome> answer = Optional.of(UnlockOutcome.UNLOCKED);
    /** When present, unlocking fails at once with this kind, as a keychain without a usable prompt. */
    public volatile Optional<KeychainException.Kind> unlockFails = Optional.empty();
    /** How often the prompt was asked for. */
    public final AtomicInteger prompts = new AtomicInteger();
    /** Counted down when the prompt shows. */
    public final CountDownLatch prompting = new CountDownLatch(1);
    private final AtomicReference<CompletableFuture<UnlockOutcome>> waiting = new AtomicReference<>();

    @Override
    public Optional<String> read(String name) throws KeychainException {
        requireUnlocked();
        return super.read(name);
    }

    @Override
    public void write(String name, String value) throws KeychainException {
        requireUnlocked();
        super.write(name, value);
    }

    @Override
    public void delete(String name) throws KeychainException {
        requireUnlocked();
        super.delete(name);
    }

    @Override
    public UnlockOutcome unlock(Duration wait, Runnable prompting) throws KeychainException {
        if (unlockFails.isPresent()) {
            throw new KeychainException(unlockFails.get(), "cannot unlock");
        }
        prompts.incrementAndGet();
        var answered = new CompletableFuture<UnlockOutcome>();
        waiting.set(answered);
        prompting.run();
        this.prompting.countDown();
        answer.ifPresent(answered::complete);
        UnlockOutcome outcome;
        try {
            outcome = answered.get(wait.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException | ExecutionException e) {
            outcome = UnlockOutcome.NOT_ANSWERED;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            outcome = UnlockOutcome.NOT_ANSWERED;
        }
        if (outcome == UnlockOutcome.UNLOCKED) {
            locked = false;
        }
        return outcome;
    }

    @Override
    public void stopUnlocking() {
        var answered = waiting.get();
        if (answered != null) {
            answered.complete(UnlockOutcome.NOT_ANSWERED);
        }
    }

    private void requireUnlocked() throws KeychainException {
        if (locked) {
            throw new KeychainException(KeychainException.Kind.LOCKED, "the keyring is locked");
        }
    }
}
