package io.github.enadeag.carchargeplanner.platform.linux;

import java.util.List;
import java.util.Map;
import org.freedesktop.dbus.DBusPath;
import org.freedesktop.dbus.Struct;
import org.freedesktop.dbus.Tuple;
import org.freedesktop.dbus.annotations.DBusInterfaceName;
import org.freedesktop.dbus.annotations.Position;
import org.freedesktop.dbus.interfaces.DBusInterface;
import org.freedesktop.dbus.types.Variant;

/**
 * The parts of the freedesktop Secret Service API (https://specifications.freedesktop.org/secret-service/) the
 * keychain uses. The names are the protocol's.
 */
public interface SecretService {

    String BUS_NAME = "org.freedesktop.secrets";
    String SERVICE_PATH = "/org/freedesktop/secrets";
    String DEFAULT_COLLECTION = "/org/freedesktop/secrets/aliases/default";
    String COLLECTION_INTERFACE = "org.freedesktop.Secret.Collection";
    /** A method that returns this path as its prompt needed no prompt. */
    String NO_PROMPT = "/";

    @DBusInterfaceName("org.freedesktop.Secret.Service")
    interface Service extends DBusInterface {

        /** @return the algorithm's output and the session's path */
        Pair<Variant<?>, DBusPath> OpenSession(String algorithm, Variant<?> input);

        /** @return the unlocked items and the locked items */
        Pair<List<DBusPath>, List<DBusPath>> SearchItems(Map<String, String> attributes);

        Map<DBusPath, Secret> GetSecrets(List<DBusPath> items, DBusPath session);
    }

    @DBusInterfaceName(COLLECTION_INTERFACE)
    interface Collection extends DBusInterface {

        /** @return the item's path and a prompt's path */
        Pair<DBusPath, DBusPath> CreateItem(Map<String, Variant<?>> properties, Secret secret, boolean replace);
    }

    @DBusInterfaceName("org.freedesktop.Secret.Item")
    interface Item extends DBusInterface {

        /** @return a prompt's path */
        DBusPath Delete();
    }

    /** A value as the protocol carries it: within a session, with its parameters and content type. */
    final class Secret extends Struct {
        @Position(0)
        public final DBusPath session;
        @Position(1)
        public final byte[] parameters;
        @Position(2)
        public final byte[] value;
        @Position(3)
        public final String contentType;

        public Secret(DBusPath session, byte[] parameters, byte[] value, String contentType) {
            this.session = session;
            this.parameters = parameters;
            this.value = value;
            this.contentType = contentType;
        }
    }

    final class Pair<A, B> extends Tuple {
        @Position(0)
        public final A first;
        @Position(1)
        public final B second;

        public Pair(A first, B second) {
            this.first = first;
            this.second = second;
        }
    }
}
