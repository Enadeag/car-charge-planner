package io.github.enadeag.carchargeplanner.platform.linux;

import io.github.enadeag.carchargeplanner.engine.secrets.Keychain;
import io.github.enadeag.carchargeplanner.engine.secrets.KeychainException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.freedesktop.dbus.DBusPath;
import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.exceptions.DBusException;
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
 */
public final class SecretServiceKeychain implements Keychain {

    private static final String LABEL_PROPERTY = "org.freedesktop.Secret.Item.Label";
    private static final String ATTRIBUTES_PROPERTY = "org.freedesktop.Secret.Item.Attributes";


    /** One step done over an open connection. */
    private interface Step<T> {
        T run(DBusConnection bus) throws DBusException, KeychainException;
    }

    private final BusConnector connector;
    private final String application;

    /**
     * Makes the keychain; nothing is connected yet.
     *
     * @param connector opens a connection for each call
     * @param application the attribute that tells this app's items from others', such as "car-charge-planner"
     */
    public SecretServiceKeychain(BusConnector connector, String application) {
        this.connector = connector;
        this.application = application;
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

    /** A connection for each call: the keys are read when a forecast is fetched, at most a few times an hour. */
    private <T> T over(Step<T> step) throws KeychainException {
        try (var bus = connector.connect()) {
            return step.run(bus);
        } catch (DBusException | RuntimeException e) {
            // no session bus, no Secret Service, or it refused; dbus-java reports a refusal as a RuntimeException
            throw new KeychainException("the Secret Service could not be used (" + e.getClass().getSimpleName() + ")", e);
        } catch (java.io.IOException e) {
            throw new KeychainException("the connection to the Secret Service did not close", e);
        }
    }
}
