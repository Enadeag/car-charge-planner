package io.github.enadeag.carchargeplanner.platform.linux;

import io.github.enadeag.carchargeplanner.desktop.system.AppIcon;
import io.github.enadeag.carchargeplanner.desktop.system.NotificationText;
import io.github.enadeag.carchargeplanner.desktop.system.Notifications;
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
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import javax.imageio.ImageIO;
import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.interfaces.DBus;
import org.freedesktop.dbus.types.UInt32;
import org.freedesktop.dbus.types.Variant;

/**
 * Notifications on Linux, through the desktop's notification server over D-Bus: the same on KDE Plasma and GNOME,
 * also without a tray. A click on the notification or on its button "Open dashboard" is passed on; a click on another
 * app's notification is not, for all apps share the server's ids. A click counts only when it comes from the
 * program that owns the server's name: any program on the session bus can send the signal.
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


    private final BusConnector connector;
    private final String appName;
    private final String desktopEntry;
    private final AppIcon icon;
    private final Path iconFolder;
    private final Supplier<String> openLabel;
    private final Executor clickRunner;
    private final Deque<Long> sent = new ArrayDeque<>();
    private Runnable clicked = () -> { };
    private DBusConnection bus;
    /** The unique bus name of the notification server; null while no program serves it. */
    private String serverOwner;
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
     * @param openLabel what the notifications' button that opens the dashboard says, in the language spoken now, such
     *     as "Open dashboard"; asked anew for each notification, never empty
     */
    public FreedesktopNotifications(BusConnector connector, String appName, String desktopEntry, AppIcon icon,
            Path iconFolder, Supplier<String> openLabel) {
        // on a thread of its own: D-Bus hands a click over on the connection's thread, which must not be held up
        this(connector, appName, desktopEntry, icon, iconFolder, openLabel,
                action -> Thread.ofVirtual().name("notification-click").start(action));
    }

    /**
     * Makes the notifications with a runner of their own for the action of a click.
     *
     * @param connector opens the session bus connection the notifications are sent and heard on
     * @param appName the app's name, which the desktop shows above each notification
     * @param desktopEntry the name of the app's launcher entry without ".desktop"
     * @param icon the app icon, shown on each notification
     * @param iconFolder an existing folder of the notifications' own, where the icon is written for the server
     * @param openLabel what the button that opens the dashboard says, asked anew for each notification
     * @param clickRunner runs the action of each click that is passed on, given on the connection's thread in the
     *     order the clicks came, such as a new thread for each
     */
    FreedesktopNotifications(BusConnector connector, String appName, String desktopEntry, AppIcon icon,
            Path iconFolder, Supplier<String> openLabel, Executor clickRunner) {
        this.connector = connector;
        this.appName = appName;
        this.desktopEntry = desktopEntry;
        this.icon = icon;
        this.iconFolder = iconFolder;
        this.openLabel = openLabel;
        this.clickRunner = clickRunner;
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
            var open = openLabel.get();
            var id = server.Notify(appName, new UInt32(0), iconUri(), text.title(), text.body(),
                    List.of(DEFAULT_ACTION, open, OPEN_ACTION, open),
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
            serverOwner = null;
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
            bus.addSigHandler(DBus.NameOwnerChanged.class, this::ownerChanged);
            bus.addSigHandler(NotificationServer.ActionInvoked.class, this::invoked);
            serverOwner = owner(bus);
            return bus;
        } catch (DBusException | RuntimeException e) {
            warnOnce("there is no session bus", e);
            bus = null;
            return null;
        }
    }

    /** The unique name of the server's owner, or null when no program serves it now. */
    private static String owner(DBusConnection connection) throws DBusException {
        var daemon = connection.getRemoteObject("org.freedesktop.DBus", "/org/freedesktop/DBus", DBus.class);
        try {
            return daemon.GetNameOwner(NotificationServer.BUS_NAME);
        } catch (RuntimeException e) {
            // the bus answers NameHasNoOwner as an error; NameOwnerChanged tells when a server comes
            return null;
        }
    }

    private synchronized void ownerChanged(DBus.NameOwnerChanged signal) {
        if (NotificationServer.BUS_NAME.equals(signal.name)) {
            serverOwner = signal.newOwner.isEmpty() ? null : signal.newOwner;
        }
    }

    private void invoked(NotificationServer.ActionInvoked signal) {
        Runnable action;
        synchronized (this) {
            if (serverOwner == null || !serverOwner.equals(signal.getSource())) {
                return;
            }
            if (!Set.of(DEFAULT_ACTION, OPEN_ACTION).contains(signal.actionKey) || !sent.contains(signal.id.longValue())) {
                return;
            }
            action = clicked;
        }
        clickRunner.execute(action);
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
