package io.github.enadeag.carchargeplanner.platform.linux;

import java.util.List;
import java.util.Map;
import org.freedesktop.dbus.annotations.DBusInterfaceName;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.interfaces.DBusInterface;
import org.freedesktop.dbus.messages.DBusSignal;
import org.freedesktop.dbus.types.UInt32;
import org.freedesktop.dbus.types.Variant;

/** The desktop's notification server (org.freedesktop.Notifications), as far as this app uses it. */
@DBusInterfaceName("org.freedesktop.Notifications")
public interface NotificationServer extends DBusInterface {

    /** The bus name the server answers on. */
    String BUS_NAME = "org.freedesktop.Notifications";
    /** The object path of the server. */
    String OBJECT_PATH = "/org/freedesktop/Notifications";

    /**
     * Shows a notification.
     *
     * @param appName the app's name as the desktop shows it above the notification
     * @param replacesId the id of a notification this one replaces, or 0 for a new one
     * @param appIcon the app's icon: a name from the icon theme or a file:// URI; empty for none
     * @param summary the title
     * @param body the text below the title; may be empty
     * @param actions pairs of an action's key and its label; the key "default" is a click on the notification itself
     * @param hints extra wishes by name, such as "desktop-entry", the app's launcher entry without ".desktop"
     * @param expireTimeout how long it stays on screen in milliseconds; -1 leaves it to the server, 0 never expires
     * @return the id the server gave the notification, never 0
     */
    UInt32 Notify(String appName, UInt32 replacesId, String appIcon, String summary, String body, List<String> actions,
            Map<String, Variant<?>> hints, int expireTimeout);

    /** The owner chose an action of a notification: clicked it, or one of its buttons. */
    class ActionInvoked extends DBusSignal {

        /** The id of the notification. */
        public final UInt32 id;
        /** The key of the action, such as "default". */
        public final String actionKey;

        /**
         * Makes the signal; dbus-java calls this for each one that arrives, and a server for each one it sends.
         *
         * @param path the object path of the server
         * @param id the id of the notification, as {@link #Notify} returned it
         * @param actionKey the key of the action, as it was given to {@link #Notify}
         * @throws DBusException when the signal cannot be made from these values
         */
        public ActionInvoked(String path, UInt32 id, String actionKey) throws DBusException {
            super(path, id, actionKey);
            this.id = id;
            this.actionKey = actionKey;
        }
    }
}
