package spike;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.freedesktop.dbus.DBusPath;
import org.freedesktop.dbus.Struct;
import org.freedesktop.dbus.annotations.DBusInterfaceName;
import org.freedesktop.dbus.annotations.Position;
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder;
import org.freedesktop.dbus.interfaces.DBusInterface;
import org.freedesktop.dbus.types.Variant;

/**
 * Question 6, Linux, own client: the freedesktop Secret Service API straight over dbus-java 5
 * ("plain" session, default collection). Never locks anything; if the collection is locked it says so
 * and stops instead of triggering an unlock prompt.
 */
public class SecretDbus {

    @DBusInterfaceName("org.freedesktop.Secret.Service")
    public interface Service extends DBusInterface {
        Sni.Pair<Variant<?>, DBusPath> OpenSession(String algorithm, Variant<?> input);
        Sni.Pair<List<DBusPath>, List<DBusPath>> SearchItems(Map<String, String> attributes);
        Map<DBusPath, SecretValue> GetSecrets(List<DBusPath> items, DBusPath session);
    }

    @DBusInterfaceName("org.freedesktop.Secret.Collection")
    public interface Collection extends DBusInterface {
        Sni.Pair<DBusPath, DBusPath> CreateItem(Map<String, Variant<?>> properties, SecretValue secret, boolean replace);
    }

    @DBusInterfaceName("org.freedesktop.Secret.Item")
    public interface Item extends DBusInterface {
        DBusPath Delete();
    }

    public static final class SecretValue extends Struct {
        @Position(0) public final DBusPath session;
        @Position(1) public final byte[] parameters;
        @Position(2) public final byte[] value;
        @Position(3) public final String contentType;
        public SecretValue(DBusPath session, byte[] parameters, byte[] value, String contentType) {
            this.session = session; this.parameters = parameters; this.value = value; this.contentType = contentType;
        }
    }

    public static void main(String[] args) throws Exception {
        try (var conn = DBusConnectionBuilder.forSessionBus().build()) {
            var bus = "org.freedesktop.secrets";
            var service = conn.getRemoteObject(bus, "/org/freedesktop/secrets", Service.class);
            var props = conn.getRemoteObject(bus, "/org/freedesktop/secrets/aliases/default",
                    org.freedesktop.dbus.interfaces.Properties.class);
            boolean locked = props.Get("org.freedesktop.Secret.Collection", "Locked");
            System.out.println("default collection locked: " + locked);
            if (locked) {
                System.out.println("collection is locked; unlock it in the keyring tool and run again (no prompt is triggered)");
                return;
            }
            var session = service.OpenSession("plain", new Variant<>("")).b;
            var attrs = Map.of("service", "car-charge-planner-spike", "account", "example-api-token");
            var value = "example-secret-" + System.nanoTime();
            var collection = conn.getRemoteObject(bus, "/org/freedesktop/secrets/aliases/default", Collection.class);
            var created = collection.CreateItem(Map.of(
                            "org.freedesktop.Secret.Item.Label", new Variant<>("Car charge planner spike: example token"),
                            "org.freedesktop.Secret.Item.Attributes", new Variant<>(attrs, "a{ss}")),
                    new SecretValue(session, new byte[0], value.getBytes(StandardCharsets.UTF_8), "text/plain"), true);
            System.out.println("created " + created.a.getPath() + " (prompt " + created.b.getPath() + ")");
            var found = service.SearchItems(attrs).a;
            var secrets = service.GetSecrets(found, session);
            var back = new String(secrets.get(found.getFirst()).value, StandardCharsets.UTF_8);
            System.out.println("read back: " + (value.equals(back) ? "OK, identical" : "MISMATCH"));
            if (!List.of(args).contains("--keep")) {
                conn.getRemoteObject(bus, created.a.getPath(), Item.class).Delete();
                System.out.println("deleted; items left: " + service.SearchItems(attrs).a.size());
            }
        }
    }
}
