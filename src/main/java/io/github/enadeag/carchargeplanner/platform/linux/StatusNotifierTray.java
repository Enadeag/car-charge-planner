package io.github.enadeag.carchargeplanner.platform.linux;

import io.github.enadeag.carchargeplanner.desktop.PanelShade;
import io.github.enadeag.carchargeplanner.desktop.StatusDot;
import io.github.enadeag.carchargeplanner.desktop.StatusIcon;
import io.github.enadeag.carchargeplanner.desktop.Tray;
import io.github.enadeag.carchargeplanner.desktop.TrayStatus;
import java.lang.System.Logger.Level;
import java.nio.file.Path;
import java.util.List;
import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.exceptions.DBusException;

/**
 * The tray icon on Linux: our own StatusNotifierItem over D-Bus. AWT's tray is not used here: on Wayland it shows a
 * grey square that ignores clicks.
 *
 * <p>The icon goes to the panel twice: by name from an {@link IconThemeFolder}, which KDE Plasma colours to suit its
 * panel, and as pixels in several sizes for panels that cannot find it by name.
 */
public final class StatusNotifierTray implements Tray {

    private static final System.Logger LOG = System.getLogger(StatusNotifierTray.class.getName());
    /** The sizes the panel can choose from, so it never has to scale the icon up. */
    private static final List<Integer> SIZES = List.of(16, 22, 24, 32, 48, 64);

    /** Opens a connection of the tray's own (not a shared one: {@link #remove()} closes it); tests use a private bus. */
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
    private final StatusIcon icon;
    private final IconThemeFolder theme;
    private final String title;
    private final MenuLabels labels;
    // the name the specification asks for: one per process and item
    private final String busName = "org.kde.StatusNotifierItem-" + ProcessHandle.current().pid() + "-1";

    private TrayStatus status = new TrayStatus(StatusDot.NONE, "");
    private DBusConnection bus;
    private TrayItem item;

    /**
     * Makes the tray; nothing appears until {@link #show(TrayActions)}.
     *
     * @param connector opens the session bus connection the icon is served on
     * @param icon draws the icon with its status dot, in every size the panel may need
     * @param iconFolder a folder of the tray's own that only this user may use, where it writes its icons for the
     *     panel; {@link #remove()} removes it again
     * @param title the name the panel shows for the icon, such as in its tooltip
     * @param labels what the entries of the icon's menu say
     */
    public StatusNotifierTray(BusConnector connector, StatusIcon icon, Path iconFolder, String title, MenuLabels labels) {
        this.connector = connector;
        this.icon = icon;
        this.theme = new IconThemeFolder(iconFolder);
        this.title = title;
        this.labels = labels;
    }

    @Override
    public synchronized void show(TrayActions actions) throws TrayUnavailable {
        theme.write(icon);
        item = new TrayItem("car-charge-planner", title, theme.path(), where -> apart(() -> actions.clicked().accept(where)));
        update();
        var menu = new TrayMenu(List.of(
                new TrayMenu.Entry(labels.open(), () -> apart(actions.open())),
                new TrayMenu.Entry(labels.quit(), () -> apart(actions.quit()))));
        try {
            bus = connector.connect();
            bus.exportObject(item);
            bus.exportObject(menu);
            bus.requestBusName(busName);
            bus.getRemoteObject(StatusNotifierWatcher.BUS_NAME, StatusNotifierWatcher.OBJECT_PATH,
                    StatusNotifierWatcher.class).RegisterStatusNotifierItem(busName);
        } catch (DBusException | RuntimeException e) {
            // no session bus, or no StatusNotifierWatcher (GNOME without the AppIndicator extension)
            remove();
            throw new TrayUnavailable("The desktop has no tray that this app can use", e);
        }
    }

    /**
     * The action on a thread of its own: D-Bus calls it on the connection's thread, and an action that removes the
     * tray would wait there for that same thread to finish.
     */
    private static void apart(Runnable action) {
        Thread.ofVirtual().name("tray-action").start(action);
    }

    @Override
    public synchronized void showStatus(TrayStatus status) {
        this.status = status;
        if (bus == null || !bus.isConnected()) {
            return;
        }
        update();
        try {
            bus.sendMessage(new StatusNotifierSignals.NewIcon(TrayItem.OBJECT_PATH));
            bus.sendMessage(new StatusNotifierSignals.NewToolTip(TrayItem.OBJECT_PATH));
        } catch (DBusException e) {
            LOG.log(Level.WARNING, "cannot tell the panel that the tray icon changed", e);
        }
    }

    /** Gives the item the icon and tooltip of the status; the panel sees them when it next reads them. */
    private void update() {
        // the pixels are for panels without the icon theme, GNOME's dark top bar among them: light shapes
        var images = SIZES.stream().map(size -> icon.image(status.dot(), PanelShade.DARK, size)).toList();
        item.show(theme.iconName(status.dot()), images, status.text());
    }

    @Override
    public synchronized void remove() {
        if (bus != null) {
            // the panel drops the icon as soon as our name leaves the bus
            bus.disconnect();
        }
        theme.delete();
    }
}
