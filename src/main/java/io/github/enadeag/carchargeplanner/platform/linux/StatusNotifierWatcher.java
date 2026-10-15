package io.github.enadeag.carchargeplanner.platform.linux;

import org.freedesktop.dbus.annotations.DBusInterfaceName;
import org.freedesktop.dbus.interfaces.DBusInterface;

/** The panel's registry of tray items. */
@DBusInterfaceName("org.kde.StatusNotifierWatcher")
public interface StatusNotifierWatcher extends DBusInterface {

    String BUS_NAME = "org.kde.StatusNotifierWatcher";
    String OBJECT_PATH = "/StatusNotifierWatcher";

    void RegisterStatusNotifierItem(String service);
}
