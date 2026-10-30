package nl.enadeag.carchargeplanner.platform.linux;

import org.freedesktop.dbus.annotations.DBusInterfaceName;
import org.freedesktop.dbus.interfaces.DBusInterface;

/** The panel's registry of tray items. */
@DBusInterfaceName("org.kde.StatusNotifierWatcher")
public interface StatusNotifierWatcher extends DBusInterface {

    /** The bus name the panel's registry answers on. */
    String BUS_NAME = "org.kde.StatusNotifierWatcher";
    /** The object path of the registry. */
    String OBJECT_PATH = "/StatusNotifierWatcher";

    /**
     * Tells the panel about a tray item.
     *
     * @param service the bus name of the item's owner
     */
    void RegisterStatusNotifierItem(String service);
}
