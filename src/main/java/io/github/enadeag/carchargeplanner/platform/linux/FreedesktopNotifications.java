package io.github.enadeag.carchargeplanner.platform.linux;

import io.github.enadeag.carchargeplanner.desktop.AppIcon;
import io.github.enadeag.carchargeplanner.desktop.NotificationText;
import io.github.enadeag.carchargeplanner.desktop.Notifications;
import java.io.IOException;
import java.lang.System.Logger.Level;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.imageio.ImageIO;
import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.types.UInt32;
import org.freedesktop.dbus.types.Variant;

/**
 * Notifications on Linux, through the desktop's notification server over D-Bus: the same on KDE Plasma and GNOME,
 * also without a tray. A click on the notification or on its button "Open dashboard" is passed on; a click on another
 * app's notification is not, for all apps share the server's ids.
 */
public final class FreedesktopNotifications implements Notifications {

    private static final System.Logger LOG = System.getLogger(FreedesktopNotifications.class.getName());
    /** The key of a click on the notification itself. */
    static final String DEFAULT_ACTION = "default";
    /** The key of the button "Open dashboard". */
    static final String OPEN_ACTION = "open";
    /** How long a notification stays on screen, in milliseconds, before it goes to the desktop's history. */
    static final int EXPIRE_MS = 20_000;
    /** How many of the latest notifications' ids are kept, to recognise a click on one of them later. */
    static final int REMEMBER = 50;
    /** The stock icon used when the app's own cannot be written. */
    static final String FALLBACK_ICON = "dialog-information";
    private static final String ICON_FILE = "car-charge-planner.png";
    private static final int ICON_SIZE = 128;

    /** Opens a connection of the notifications' own (not a shared one: {@link #close()} closes it). */
    public interface BusConnector {

        /**
         * Opens the connection.
         *
         * @return a new connection, not shared with other parts of the app
         * @throws DBusException when there is no bus to connect to
         */
        DBusConnection connect() throws DBusException;
    }

    private final BusConnector connector;
    private final String appName;
    private final String desktopEntry;
    private final AppIcon icon;
    private final Path iconFolder;
    private final String openLabel;
    private final Deque<Long> sent = new ArrayDeque<>();
    private Runnable clicked = () -> { };
    private DBusConnection bus;
    private String iconUri;
    private boolean warned;

    /**
     * Makes the notifications; nothing is connected or written until {@link #listen} or {@link #show}.
     *
     * @param connector opens the session bus connection the notifications are sent and heard on
     * @param appName the app's name, which the desktop shows above each notification, such as "Car charge planner"
     * @param desktopEntry the name of the app's launcher entry without ".desktop", which ties the notifications to the
     *     app in the desktop's settings; it need not be installed
     * @param icon the app icon, shown on each notification
     * @param iconFolder an existing folder of the notifications' own that only this user may use, where the icon is
     *     written for the server to read; {@link #close()} removes it again
     * @param openLabel what the notifications' button that opens the dashboard says, in the owner's language, such as
     *     "Open dashboard"; not empty
     */
    public FreedesktopNotifications(BusConnector connector, String appName, String desktopEntry, AppIcon icon,
            Path iconFolder, String openLabel) {
        this.connector = connector;
        this.appName = appName;
        this.desktopEntry = desktopEntry;
        this.icon = icon;
        this.iconFolder = iconFolder;
        this.openLabel = openLabel;
    }

    @Override
    public synchronized void listen(Runnable clicked) {
        this.clicked = clicked;
        connected();
    }

    @Override
    public synchronized void show(NotificationText text) {
        var connection = connected();
        if (connection == null) {
            return;
        }
        try {
            var server = connection.getRemoteObject(NotificationServer.BUS_NAME, NotificationServer.OBJECT_PATH,
                    NotificationServer.class);
            var id = server.Notify(appName, new UInt32(0), iconUri(), text.title(), text.body(),
                    List.of(DEFAULT_ACTION, openLabel, OPEN_ACTION, openLabel),
                    Map.of("desktop-entry", new Variant<>(desktopEntry)), EXPIRE_MS);
            sent.addLast(id.longValue());
            if (sent.size() > REMEMBER) {
                sent.removeFirst();
            }
        } catch (DBusException | RuntimeException e) {
            // dbus-java reports a server that is not there or answers with an error as a runtime exception
            warnOnce("the desktop's notification server did not take it", e);
        }
    }

    @Override
    public synchronized void close() {
        if (bus != null) {
            bus.disconnect();
            bus = null;
        }
        try {
            Files.deleteIfExists(iconFolder.resolve(ICON_FILE));
            Files.deleteIfExists(iconFolder);
        } catch (IOException e) {
            LOG.log(Level.WARNING, "cannot remove the notification icon from " + iconFolder, e);
        }
    }

    /** The connection, opened and listening for clicks when it is not yet; null when there is no session bus. */
    private DBusConnection connected() {
        if (bus != null && bus.isConnected()) {
            return bus;
        }
        try {
            bus = connector.connect();
            bus.addSigHandler(NotificationServer.ActionInvoked.class, this::invoked);
            return bus;
        } catch (DBusException | RuntimeException e) {
            warnOnce("there is no session bus", e);
            bus = null;
            return null;
        }
    }

    private void invoked(NotificationServer.ActionInvoked signal) {
        Runnable action;
        synchronized (this) {
            if (!Set.of(DEFAULT_ACTION, OPEN_ACTION).contains(signal.actionKey) || !sent.contains(signal.id.longValue())) {
                return;
            }
            action = clicked;
        }
        // on a thread of its own: D-Bus calls this on the connection's thread, which the action must not hold up
        Thread.ofVirtual().name("notification-click").start(action);
    }

    /** The app icon as a file the server can read, written once; a stock icon when it cannot be written. */
    private String iconUri() {
        if (iconUri == null) {
            var file = iconFolder.resolve(ICON_FILE);
            try {
                // never through a link another user may have put there
                try (var out = Files.newOutputStream(file, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                        StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
                    ImageIO.write(icon.image(ICON_SIZE), "png", out);
                }
                iconUri = file.toAbsolutePath().toUri().toString();
            } catch (IOException e) {
                LOG.log(Level.WARNING, "cannot write the notification icon to " + file + "; using a stock icon", e);
                iconUri = FALLBACK_ICON;
            }
        }
        return iconUri;
    }

    /** Once, so a desktop without a notification server does not fill the log at every announcement. */
    private void warnOnce(String why, Exception e) {
        if (!warned) {
            warned = true;
            LOG.log(Level.WARNING, "Could not show a notification: " + why, e);
        }
    }
}
