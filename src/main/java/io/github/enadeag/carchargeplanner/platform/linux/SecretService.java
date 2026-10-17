package io.github.enadeag.carchargeplanner.platform.linux;

import java.util.List;
import java.util.Map;
import org.freedesktop.dbus.DBusPath;
import org.freedesktop.dbus.Struct;
import org.freedesktop.dbus.Tuple;
import org.freedesktop.dbus.annotations.DBusInterfaceName;
import org.freedesktop.dbus.annotations.MethodNoReply;
import org.freedesktop.dbus.annotations.Position;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.interfaces.DBusInterface;
import org.freedesktop.dbus.messages.DBusSignal;
import org.freedesktop.dbus.types.Variant;

/**
 * The parts of the freedesktop Secret Service API (https://specifications.freedesktop.org/secret-service/) the
 * keychain uses. The names are the protocol's.
 */
public interface SecretService {

    /** The bus name the Secret Service owns. */
    String BUS_NAME = "org.freedesktop.secrets";
    /** The object that offers the {@link Service} interface. */
    String SERVICE_PATH = "/org/freedesktop/secrets";
    /** The default collection, normally the login keyring. */
    String DEFAULT_COLLECTION = "/org/freedesktop/secrets/aliases/default";
    /** The interface name of a collection, also for reading its properties. */
    String COLLECTION_INTERFACE = "org.freedesktop.Secret.Collection";
    /** A method that returns this path as its prompt needed no prompt; an alias that stands for nothing gives it too. */
    String NO_PROMPT = "/";
    /** The alias of the default collection, normally the login keyring. */
    String DEFAULT_ALIAS = "default";

    /** The service: sessions and finding items. */
    @DBusInterfaceName("org.freedesktop.Secret.Service")
    interface Service extends DBusInterface {

        /**
         * Opens a session in which secrets are passed.
         *
         * @param algorithm how secrets are protected in the session, such as "plain"
         * @param input the algorithm's input; an empty text for "plain"
         * @return the algorithm's output and the session's path
         */
        Pair<Variant<?>, DBusPath> OpenSession(String algorithm, Variant<?> input);

        /**
         * Finds the items whose attributes include these.
         *
         * @param attributes the attributes to match, by name
         * @return the unlocked items and the locked items
         */
        Pair<List<DBusPath>, List<DBusPath>> SearchItems(Map<String, String> attributes);

        /**
         * Reads the secrets of unlocked items.
         *
         * @param items the items to read
         * @param session the session to pass the secrets in
         * @return each item's secret, by the item's path
         */
        Map<DBusPath, Secret> GetSecrets(List<DBusPath> items, DBusPath session);

        /**
         * Unlocks collections or items, as far as that needs no prompt.
         *
         * @param objects the collections or items to unlock
         * @return the objects that are unlocked now, and a prompt's path, or {@link #NO_PROMPT} when none is needed
         */
        Pair<List<DBusPath>, DBusPath> Unlock(List<DBusPath> objects);

        /**
         * Finds the collection an alias stands for.
         *
         * @param name the alias, such as "default"
         * @return the collection's path, or {@link #NO_PROMPT} ("/") when the alias stands for none
         */
        DBusPath ReadAlias(String name);
    }

    /**
     * A prompt the service made for an action that needs the owner, such as unlocking: the service shows it once it is
     * asked to, and tells how it ended with {@link Completed}.
     */
    @DBusInterfaceName("org.freedesktop.Secret.Prompt")
    interface Prompt extends DBusInterface {

        /**
         * Shows the prompt; it returns at once, and {@link Completed} follows when the owner answered.
         *
         * @param windowId the platform's id of the window to show it over, or an empty text for none
         */
        void Prompt(String windowId);

        /**
         * Takes the prompt away unanswered; {@link Completed} follows, dismissed. Sent without waiting for the reply,
         * as nothing is done with it.
         */
        @MethodNoReply
        void Dismiss();

        /** The prompt ended. */
        class Completed extends DBusSignal {

            /** True when the owner, or a call of {@link Prompt#Dismiss()}, dismissed it. */
            public final boolean dismissed;
            /** What the action gave, such as the objects that were unlocked; its type depends on the action. */
            public final Variant<?> result;

            /**
             * Makes the signal; dbus-java calls this for each one that arrives, and a service for each one it sends.
             *
             * @param path the prompt's object path
             * @param dismissed whether the prompt was dismissed
             * @param result what the action gave
             * @throws DBusException when the signal cannot be made from these values
             */
            public Completed(String path, boolean dismissed, Variant<?> result) throws DBusException {
                super(path, dismissed, result);
                this.dismissed = dismissed;
                this.result = result;
            }
        }
    }

    /** A collection of items, such as a keyring. */
    @DBusInterfaceName(COLLECTION_INTERFACE)
    interface Collection extends DBusInterface {

        /**
         * Stores an item in the collection.
         *
         * @param properties the item's label and attributes, by property name
         * @param secret the value to keep
         * @param replace whether an item with the same attributes is replaced rather than added to
         * @return the item's path and a prompt's path
         */
        Pair<DBusPath, DBusPath> CreateItem(Map<String, Variant<?>> properties, Secret secret, boolean replace);
    }

    /** One stored item. */
    @DBusInterfaceName("org.freedesktop.Secret.Item")
    interface Item extends DBusInterface {

        /**
         * Removes the item.
         *
         * @return a prompt's path
         */
        DBusPath Delete();
    }

    /** A value as the protocol carries it: within a session, with its parameters and content type. */
    final class Secret extends Struct {
        /** The session it is passed in. */
        @Position(0)
        public final DBusPath session;
        /** The algorithm's parameters; empty for "plain". */
        @Position(1)
        public final byte[] parameters;
        /** The value itself. */
        @Position(2)
        public final byte[] value;
        /** The value's media type, such as "text/plain; charset=utf8". */
        @Position(3)
        public final String contentType;

        /**
         * Makes the value to pass.
         *
         * @param session the session it is passed in
         * @param parameters the algorithm's parameters; empty for "plain"
         * @param value the value itself
         * @param contentType the value's media type
         */
        public Secret(DBusPath session, byte[] parameters, byte[] value, String contentType) {
            this.session = session;
            this.parameters = parameters;
            this.value = value;
            this.contentType = contentType;
        }
    }

    /**
     * Two values a method returns together.
     *
     * @param <A> the first value's type
     * @param <B> the second value's type
     */
    final class Pair<A, B> extends Tuple {
        /** The first value. */
        @Position(0)
        public final A first;
        /** The second value. */
        @Position(1)
        public final B second;

        /**
         * Makes the pair.
         *
         * @param first the first value
         * @param second the second value
         */
        public Pair(A first, B second) {
            this.first = first;
            this.second = second;
        }
    }
}
