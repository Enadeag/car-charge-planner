package nl.enadeag.carchargeplanner.platform.linux;

import java.util.Map;
import org.freedesktop.dbus.DBusPath;
import org.freedesktop.dbus.annotations.DBusInterfaceName;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.interfaces.DBusInterface;
import org.freedesktop.dbus.messages.DBusSignal;
import org.freedesktop.dbus.types.UInt32;
import org.freedesktop.dbus.types.Variant;

/**
 * The parts of the XDG desktop portal's Background API (https://flatpak.github.io/xdg-desktop-portal/) that start at
 * login uses inside a Flatpak sandbox. The names are the protocol's.
 */
public interface BackgroundPortal {

    /** The bus name the desktop portal owns. */
    String BUS_NAME = "org.freedesktop.portal.Desktop";
    /** The object that offers the portal's interfaces. */
    String OBJECT_PATH = "/org/freedesktop/portal/desktop";
    /** The folder of the request objects, one per call, below which each caller has a folder of its own. */
    String REQUEST_FOLDER = OBJECT_PATH + "/request/";

    /** Asks the desktop to let the app run in the background, and to start it at login. */
    @DBusInterfaceName("org.freedesktop.portal.Background")
    interface Background extends DBusInterface {

        /**
         * Asks; the desktop may ask the owner first, and answers with {@link Request.Response} on the request object.
         *
         * @param parentWindow the platform's id of the window to show a question over, or an empty text for none
         * @param options the wishes by name, such as "autostart", "commandline" and "handle_token"
         * @return the path of the request object that answers
         */
        DBusPath RequestBackground(String parentWindow, Map<String, Variant<?>> options);
    }

    /** One call's request object, which says how the call ended. */
    @DBusInterfaceName("org.freedesktop.portal.Request")
    interface Request extends DBusInterface {

        /** The answer: 0 granted, 1 cancelled by the owner, 2 ended otherwise. */
        class Response extends DBusSignal {

            /** How the call ended: 0 granted, 1 cancelled by the owner, 2 ended otherwise. */
            public final UInt32 response;
            /** What was granted by name, such as "autostart". */
            public final Map<String, Variant<?>> results;

            /**
             * Makes the signal; dbus-java calls this for each one that arrives, and a portal for each one it sends.
             *
             * @param path the request object's path
             * @param response how the call ended
             * @param results what was granted
             * @throws DBusException when the signal cannot be made from these values
             */
            public Response(String path, UInt32 response, Map<String, Variant<?>> results) throws DBusException {
                super(path, response, results);
                this.response = response;
                this.results = results;
            }
        }
    }
}
