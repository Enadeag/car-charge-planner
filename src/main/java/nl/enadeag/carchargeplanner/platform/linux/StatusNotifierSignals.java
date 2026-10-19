package nl.enadeag.carchargeplanner.platform.linux;

import org.freedesktop.dbus.annotations.DBusInterfaceName;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.interfaces.DBusInterface;
import org.freedesktop.dbus.messages.DBusSignal;

/**
 * The signals of the tray icon protocol that tell the panel to read the item's properties again. dbus-java takes a
 * signal's interface from the interface it is declared in, so they live here, under the protocol's name, apart from
 * the methods in {@link StatusNotifierItem}. The names are the protocol's own.
 */
@DBusInterfaceName("org.kde.StatusNotifierItem")
public interface StatusNotifierSignals extends DBusInterface {

    /** The icon changed: the panel reads {@code IconName} and {@code IconPixmap} again. */
    class NewIcon extends DBusSignal {

        /**
         * Makes the signal.
         *
         * @param path the object path of the item whose icon changed, such as "/StatusNotifierItem"
         * @throws DBusException when the path is not a valid object path
         */
        public NewIcon(String path) throws DBusException {
            super(path);
        }
    }

    /** The tooltip changed: the panel reads {@code ToolTip} again. */
    class NewToolTip extends DBusSignal {

        /**
         * Makes the signal.
         *
         * @param path the object path of the item whose tooltip changed, such as "/StatusNotifierItem"
         * @throws DBusException when the path is not a valid object path
         */
        public NewToolTip(String path) throws DBusException {
            super(path);
        }
    }
}
