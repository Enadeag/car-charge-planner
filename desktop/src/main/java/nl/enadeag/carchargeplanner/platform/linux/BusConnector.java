package nl.enadeag.carchargeplanner.platform.linux;

import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder;
import org.freedesktop.dbus.exceptions.DBusException;

/**
 * Opens a D-Bus connection of the caller's own, not one shared with other parts of the app: the part that opens it
 * closes it again. Tests connect to a private bus instead.
 */
@FunctionalInterface
public interface BusConnector {

    /**
     * Opens the connection.
     *
     * @return a new connection, which the caller closes
     * @throws DBusException when there is no bus to connect to
     */
    DBusConnection connect() throws DBusException;

    /**
     * The connector to the user's session bus, where the tray, the notification server and the Secret Service are.
     *
     * @return a connector that opens a new, unshared session bus connection each time
     */
    static BusConnector sessionBus() {
        return () -> DBusConnectionBuilder.forSessionBus().withShared(false).build();
    }
}
