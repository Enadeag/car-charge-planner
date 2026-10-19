package nl.enadeag.carchargeplanner.platform.linux;

import nl.enadeag.carchargeplanner.engine.secrets.Keychain;
import nl.enadeag.carchargeplanner.engine.secrets.KeychainException;
import nl.enadeag.carchargeplanner.engine.secrets.UnlockOutcome;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import org.freedesktop.dbus.DBusPath;
import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.errors.NoReply;
import org.freedesktop.dbus.errors.ServiceUnknown;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.exceptions.DBusExecutionException;
import org.freedesktop.dbus.interfaces.DBus;
import org.freedesktop.dbus.interfaces.Properties;
import org.freedesktop.dbus.types.Variant;

/**
 * The keychain on Linux: our own small client of the Secret Service (GNOME Keyring, KWallet) over D-Bus. Libraries
 * such as java-keyring are built for an older dbus-java and cannot sit next to the one the tray uses.
 *
 * <p>Items are found by two attributes, the application and the entry's name, in the default collection (the login
 * keyring). A locked collection is reported as {@link KeychainException.Kind#LOCKED} by reads and writes, which never
 * prompt; only {@link #unlock(Duration, Runnable)}, which the engine calls when the owner saves, has the service show its own
 * prompt for the keyring's password.
 *
 * <p>The session uses the "plain" algorithm: the value crosses the owner's own session bus unencrypted, as with any
 * other message on it; the bus is not reachable by other users.
 *
 * <p>One connection of its own is kept open from the first call until {@link #close()}, and made again after a call
 * failed. A new connection first asks the bus whether a Secret Service is there or can be started, so a desktop
 * without one costs no wait. Each call gets {@link #REPLY_TIMEOUT} to answer: dbus-java's own wait is 20 seconds per
 * message, for the whole process, and a call here sends several. Calls take turns; the wait for the owner's answer to
 * a prompt is not a call, so reads go on meanwhile.
 */
public final class SecretServiceKeychain implements Keychain {

    /** How long one call (a read, a write or a delete, of several messages) may take before it counts as failed. */
    public static final Duration REPLY_TIMEOUT = Duration.ofSeconds(5);

    private static final String LABEL_PROPERTY = "org.freedesktop.Secret.Item.Label";
    private static final String ATTRIBUTES_PROPERTY = "org.freedesktop.Secret.Item.Attributes";

    /** One step done over the open connection. */
    private interface Step<T> {
        T run(DBusConnection bus) throws DBusException, KeychainException;
    }

    /**
     * A prompt that shows, and the handler that waits for its end.
     *
     * @param bus the connection it was asked for on, which the service ties it to
     * @param path the prompt's object path
     * @param listening removes the handler again
     */
    private record Shown(DBusConnection bus, String path, AutoCloseable listening) {
    }

    private final BusConnector connector;
    private final String application;
    private final Duration replyTimeout;
    private final Object turn = new Object();
    /** The connection, once made; set by the call that made it, which may finish after its caller gave up. */
    private final AtomicReference<DBusConnection> connection = new AtomicReference<>();
    /** The owner's answer an unlock waits for; null while none waits. */
    private final AtomicReference<CompletableFuture<UnlockOutcome>> answer = new AtomicReference<>();

    /**
     * Makes the keychain with the usual reply timeout; nothing is connected yet.
     *
     * @param connector opens the keychain's own connection, at the first call and again after one failed
     * @param application the attribute that tells this app's items from others', such as "car-charge-planner"
     */
    public SecretServiceKeychain(BusConnector connector, String application) {
        this(connector, application, REPLY_TIMEOUT);
    }

    /**
     * Makes the keychain; nothing is connected yet.
     *
     * @param connector opens the keychain's own connection, at the first call and again after one failed
     * @param application the attribute that tells this app's items from others', such as "car-charge-planner"
     * @param replyTimeout how long one call may take, more than zero
     * @throws IllegalArgumentException when the timeout is null, zero or negative
     */
    public SecretServiceKeychain(BusConnector connector, String application, Duration replyTimeout) {
        if (replyTimeout == null || replyTimeout.isNegative() || replyTimeout.isZero()) {
            throw new IllegalArgumentException("the reply timeout must be more than zero, not " + replyTimeout);
        }
        this.connector = connector;
        this.application = application;
        this.replyTimeout = replyTimeout;
    }

    @Override
    public Optional<String> read(String name) throws KeychainException {
        return over(bus -> {
            var found = service(bus).SearchItems(attributes(name));
            if (found.first.isEmpty()) {
                if (!found.second.isEmpty()) {
                    throw locked();
                }
                return Optional.<String>empty();
            }
            var session = openSession(bus);
            var secret = service(bus).GetSecrets(List.of(found.first.getFirst()), session).values().iterator().next();
            return Optional.of(new String(secret.value, StandardCharsets.UTF_8));
        });
    }

    @Override
    public void write(String name, String value) throws KeychainException {
        over(bus -> {
            requireUnlocked(bus);
            var collection = bus.getRemoteObject(SecretService.BUS_NAME, SecretService.DEFAULT_COLLECTION,
                    SecretService.Collection.class);
            var secret = new SecretService.Secret(openSession(bus), new byte[0], value.getBytes(StandardCharsets.UTF_8),
                    "text/plain; charset=utf8");
            var created = collection.CreateItem(Map.of(
                    LABEL_PROPERTY, new Variant<>("Car charge planner: " + name),
                    ATTRIBUTES_PROPERTY, new Variant<>(attributes(name), "a{ss}")), secret, true);
            if (!SecretService.NO_PROMPT.equals(created.second.getPath())) {
                throw locked(); // it would need the owner's password
            }
            return null;
        });
    }

    @Override
    public void delete(String name) throws KeychainException {
        over(bus -> {
            for (var item : service(bus).SearchItems(attributes(name)).first) {
                bus.getRemoteObject(SecretService.BUS_NAME, item.getPath(), SecretService.Item.class).Delete();
            }
            return null;
        });
    }

    /**
     * {@inheritDoc}
     *
     * <p>Asks the service to unlock the default collection. When that needs the owner, the service's own prompt shows
     * (GNOME's or KDE's password dialog, not over any window of the app), and this waits for its {@code Completed}
     * signal; a prompt not answered in time, or no longer waited for, is dismissed.
     *
     * @throws KeychainException {@link KeychainException.Kind#UNAVAILABLE} when there is no Secret Service;
     *     {@link KeychainException.Kind#REFUSED} when there is no default collection, or the service refused to unlock
     *     it without showing a prompt
     * @throws IllegalArgumentException when the wait is null, zero or negative
     */
    @Override
    public UnlockOutcome unlock(Duration wait, Runnable prompting) throws KeychainException {
        if (wait == null || wait.isNegative() || wait.isZero()) {
            throw new IllegalArgumentException("the wait must be more than zero, not " + wait);
        }
        var waiting = new CompletableFuture<UnlockOutcome>();
        var before = answer.getAndSet(waiting);
        if (before != null) {
            before.complete(UnlockOutcome.NOT_ANSWERED);
        }
        Optional<Shown> shown = Optional.empty();
        try {
            try {
                shown = over(bus -> showPrompt(bus, waiting));
            } catch (KeychainException e) {
                if (waiting.isDone()) {
                    return UnlockOutcome.NOT_ANSWERED; // stopped, such as by close(), while the prompt was asked for
                }
                throw e;
            }
            if (shown.isEmpty()) {
                return UnlockOutcome.UNLOCKED;
            }
            prompting.run();
            var outcome = waitFor(waiting, wait);
            if (outcome == UnlockOutcome.NOT_ANSWERED) {
                dismiss(shown.get());
            }
            return outcome;
        } finally {
            answer.compareAndSet(waiting, null);
            shown.ifPresent(prompt -> stopListening(prompt.listening()));
        }
    }

    /** Ends the wait of {@link #unlock(Duration, Runnable)}, which then dismisses its prompt. */
    @Override
    public void stopUnlocking() {
        var waiting = answer.get();
        if (waiting != null) {
            waiting.complete(UnlockOutcome.NOT_ANSWERED);
        }
    }

    /** Stops waiting for an unlock, and closes the connection, when there is one; a later call makes a new one. */
    @Override
    public void close() {
        stopUnlocking();
        disconnect();
    }

    /**
     * Asks to unlock the default collection, and shows the prompt when the service made one, listening for its end
     * first; empty when the collection is unlocked without one.
     */
    private Optional<Shown> showPrompt(DBusConnection bus, CompletableFuture<UnlockOutcome> waiting)
            throws DBusException, KeychainException {
        var collection = service(bus).ReadAlias(SecretService.DEFAULT_ALIAS);
        if (SecretService.NO_PROMPT.equals(collection.getPath())) {
            throw new KeychainException(KeychainException.Kind.REFUSED, "there is no default keyring to unlock");
        }
        var asked = service(bus).Unlock(List.of(collection));
        var prompt = asked.second.getPath();
        if (SecretService.NO_PROMPT.equals(prompt)) {
            if (asked.first.stream().noneMatch(path -> path.getPath().equals(collection.getPath()))) {
                throw new KeychainException(KeychainException.Kind.REFUSED, "the keyring was not unlocked");
            }
            return Optional.empty();
        }
        var listening = bus.addSigHandler(SecretService.Prompt.Completed.class, completed -> {
            if (prompt.equals(completed.getPath())) {
                waiting.complete(completed.dismissed ? UnlockOutcome.DISMISSED : UnlockOutcome.UNLOCKED);
            }
        });
        try {
            bus.getRemoteObject(SecretService.BUS_NAME, prompt, SecretService.Prompt.class).Prompt("");
        } catch (RuntimeException e) {
            stopListening(listening);
            throw e;
        }
        return Optional.of(new Shown(bus, prompt, listening));
    }

    /** The owner's answer, or not answered when it does not come in time or the wait is stopped or interrupted. */
    private static UnlockOutcome waitFor(CompletableFuture<UnlockOutcome> waiting, Duration wait) {
        try {
            return waiting.get(wait.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException | ExecutionException e) {
            return UnlockOutcome.NOT_ANSWERED;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return UnlockOutcome.NOT_ANSWERED;
        }
    }

    /**
     * Takes a prompt that was not answered away, on the connection it belongs to and without waiting for the answer,
     * so it never makes a new connection, such as while the app closes. A prompt that is gone already, or went with
     * its connection, is no matter.
     */
    private static void dismiss(Shown prompt) {
        if (!prompt.bus().isConnected()) {
            return;
        }
        try {
            prompt.bus().getRemoteObject(SecretService.BUS_NAME, prompt.path(), SecretService.Prompt.class).Dismiss();
        } catch (DBusException | RuntimeException e) {
            // the prompt ended or went with the connection
        }
    }

    private static void stopListening(AutoCloseable listening) {
        try {
            listening.close();
        } catch (Exception e) {
            // the handler went with the connection
        }
    }

    private Map<String, String> attributes(String name) {
        return Map.of("application", application, "name", name);
    }

    private static SecretService.Service service(DBusConnection bus) throws DBusException {
        return bus.getRemoteObject(SecretService.BUS_NAME, SecretService.SERVICE_PATH, SecretService.Service.class);
    }

    private static DBusPath openSession(DBusConnection bus) throws DBusException {
        return service(bus).OpenSession("plain", new Variant<>("")).second;
    }

    private static void requireUnlocked(DBusConnection bus) throws DBusException, KeychainException {
        var properties = bus.getRemoteObject(SecretService.BUS_NAME, SecretService.DEFAULT_COLLECTION, Properties.class);
        Boolean locked = properties.Get(SecretService.COLLECTION_INTERFACE, "Locked");
        if (locked) {
            throw locked();
        }
    }

    private static KeychainException locked() {
        return new KeychainException(KeychainException.Kind.LOCKED, "the keyring is locked");
    }

    /**
     * Runs a step over the connection, in time. The step runs on a thread of its own, as dbus-java's calls block:
     * when it takes too long, the connection is closed, which ends the step's wait for its answer.
     */
    private <T> T over(Step<T> step) throws KeychainException {
        synchronized (turn) {
            var call = new FutureTask<T>(() -> step.run(connected()));
            Thread.ofVirtual().name("keychain").start(call);
            try {
                return call.get(replyTimeout.toMillis(), TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                disconnect();
                throw new KeychainException(KeychainException.Kind.UNAVAILABLE,
                        "the Secret Service did not answer within " + replyTimeout.toMillis() + " ms");
            } catch (InterruptedException e) {
                disconnect();
                Thread.currentThread().interrupt();
                throw new KeychainException(KeychainException.Kind.UNAVAILABLE,
                        "interrupted while waiting for the Secret Service", e);
            } catch (ExecutionException e) {
                if (e.getCause() instanceof KeychainException refused) {
                    throw refused; // the Secret Service answered: the connection is fine
                }
                // no session bus, no Secret Service, or it refused; dbus-java reports a refusal as a RuntimeException
                disconnect();
                throw new KeychainException(kind(e.getCause()), "the Secret Service could not be used ("
                        + (e.getCause() == null ? "unknown" : e.getCause().getClass().getSimpleName()) + ")",
                        e.getCause());
            }
        }
    }

    /**
     * No bus, no service or no answer is unavailable; another error the service answered with is a refusal. dbus-java
     * gives the bus's own errors as classes of their own, and the service's as a plain DBusExecutionException.
     */
    private static KeychainException.Kind kind(Throwable failure) {
        if (failure instanceof ServiceUnknown || failure instanceof NoReply) {
            return KeychainException.Kind.UNAVAILABLE;
        }
        return failure instanceof DBusExecutionException ? KeychainException.Kind.REFUSED
                : KeychainException.Kind.UNAVAILABLE;
    }

    /** The open connection, or a new one to a bus that has a Secret Service. */
    private DBusConnection connected() throws DBusException, KeychainException {
        var open = connection.get();
        if (open != null && open.isConnected()) {
            return open;
        }
        var made = connector.connect();
        try {
            requireSecretService(made);
        } catch (DBusException | KeychainException | RuntimeException e) {
            closeQuietly(made);
            throw e;
        }
        var before = connection.getAndSet(made);
        if (before != null) {
            closeQuietly(before);
        }
        return made;
    }

    /** Asks the bus itself, which always answers, so a call is never sent to a name nobody owns or can start. */
    private static void requireSecretService(DBusConnection bus) throws DBusException, KeychainException {
        var daemon = bus.getRemoteObject("org.freedesktop.DBus", "/org/freedesktop/DBus", DBus.class);
        if (!Arrays.asList(daemon.ListNames()).contains(SecretService.BUS_NAME)
                && !Arrays.asList(daemon.ListActivatableNames()).contains(SecretService.BUS_NAME)) {
            throw new KeychainException(KeychainException.Kind.UNAVAILABLE,
                    "there is no Secret Service on the session bus");
        }
    }

    /** Closes the connection; an unlock that waits for a signal on it then ends unanswered, as none can come. */
    private void disconnect() {
        var open = connection.getAndSet(null);
        if (open != null) {
            closeQuietly(open);
            stopUnlocking();
        }
    }

    private static void closeQuietly(DBusConnection bus) {
        try {
            bus.close();
        } catch (IOException e) {
            // it is not used again either way
        }
    }
}
