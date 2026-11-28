package nl.enadeag.carchargeplanner.platform.linux;

import org.freedesktop.dbus.annotations.DBusInterfaceName;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.interfaces.DBusInterface;
import org.freedesktop.dbus.messages.DBusSignal;
import org.freedesktop.dbus.types.Variant;

/**
 * The XDG desktop portal's Settings API (https://flatpak.github.io/xdg-desktop-portal/), which hands the desktop's
 * settings to an app, also inside a Flatpak sandbox. The names are the protocol's.
 */
@DBusInterfaceName("org.freedesktop.portal.Settings")
public interface SettingsPortal extends DBusInterface {

    /** The bus name the desktop portal owns. */
    String BUS_NAME = BackgroundPortal.BUS_NAME;
    /** The object that offers the portal's interfaces. */
    String OBJECT_PATH = BackgroundPortal.OBJECT_PATH;

    /**
     * Reads one setting; from version 2 of the interface.
     *
     * @param namespace the setting's namespace, such as "org.gnome.desktop.interface"
     * @param key the setting's key, such as "clock-format"
     * @return the setting's value
     */
    Variant<?> ReadOne(String namespace, String key);

    /**
     * Reads one setting, as older portals offer it; the value comes wrapped in a second variant.
     *
     * @param namespace the setting's namespace
     * @param key the setting's key
     * @return the setting's value, in a variant of its own
     */
    Variant<?> Read(String namespace, String key);

    /** A setting changed. */
    class SettingChanged extends DBusSignal {

        /** The setting's namespace, such as "org.gnome.desktop.interface". */
        public final String namespace;
        /** The setting's key, such as "clock-format". */
        public final String key;
        /** The setting's new value. */
        public final Variant<?> value;

        /**
         * Makes the signal; dbus-java calls this for each one that arrives, and a portal for each one it sends.
         *
         * @param path the portal's object path
         * @param namespace the setting's namespace
         * @param key the setting's key
         * @param value the setting's new value
         * @throws DBusException when the signal cannot be made from these values
         */
        public SettingChanged(String path, String namespace, String key, Variant<?> value) throws DBusException {
            super(path, namespace, key, value);
            this.namespace = namespace;
            this.key = key;
            this.value = value;
        }
    }
}
