package io.github.enadeag.carchargeplanner.platform.linux;

import io.github.enadeag.carchargeplanner.engine.secrets.Keychain;
import io.github.enadeag.carchargeplanner.engine.secrets.KeychainException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import org.freedesktop.dbus.DBusPath;
import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.interfaces.DBus;
import org.freedesktop.dbus.interfaces.Properties;
import org.freedesktop.dbus.types.Variant;

/**
 * The keychain on Linux: our own small client of the Secret Service (GNOME Keyring, KWallet) over D-Bus. Libraries
 * such as java-keyring are built for an older dbus-java and cannot sit next to the one the tray uses.
 *
 * <p>Items are found by two attributes, the application and the entry's name, in the default collection (the login
 * keyring). A locked collection is reported, never unlocked: unlocking would ask the owner for the keyring's password
 * at a moment they did not expect, so the engine keeps the keys in its private file until the keyring is unlocked.
 *
 * <p>The session uses the "plain" algorithm: the value crosses the owner's own session bus unencrypted, as with any
 * other message on it; the bus is not reachable by other users.
 *
 * <p>One connection of its own is kept open from the first call until {@link #close()}, and made again after a call
 * failed. A new connection first asks the bus whether a Secret Service is there or can be started, so a desktop
 * without one costs no wait. Each call gets {@link #REPLY_TIMEOUT} to answer: dbus-java's own wait is 20 seconds per
 * message, for the whole process, and a call here sends several. Calls take turns.
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

    private final BusConnector connector;
    private final String application;
    private final Duration replyTimeout;
    private final Object turn = new Object();
    /** The connection, once made; set by the call that made it, which may finish after its caller gave up. */
    private final AtomicReference<DBusConnection> connection = new AtomicReference<>();

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

    /** Closes the connection, when there is one; a later call makes a new one. */
    @Override
    public void close() {
        disconnect();
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
        return new KeychainException("the keyring is locked");
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
                throw new KeychainException(
                        "the Secret Service did not answer within " + replyTimeout.toMillis() + " ms");
            } catch (InterruptedException e) {
                disconnect();
                Thread.currentThread().interrupt();
                throw new KeychainException("interrupted while waiting for the Secret Service", e);
            } catch (ExecutionException e) {
                if (e.getCause() instanceof KeychainException refused) {
                    throw refused; // the Secret Service answered: the connection is fine
                }
                // no session bus, no Secret Service, or it refused; dbus-java reports a refusal as a RuntimeException
                disconnect();
                var kind = e.getCause() == null ? "unknown" : e.getCause().getClass().getSimpleName();
                throw new KeychainException("the Secret Service could not be used (" + kind + ")", e.getCause());
            }
        }
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
            throw new KeychainException("there is no Secret Service on the session bus");
        }
    }

    private void disconnect() {
        var open = connection.getAndSet(null);
        if (open != null) {
            closeQuietly(open);
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
