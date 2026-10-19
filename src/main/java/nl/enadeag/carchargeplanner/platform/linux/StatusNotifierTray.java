package nl.enadeag.carchargeplanner.platform.linux;

import nl.enadeag.carchargeplanner.desktop.system.PanelShade;
import nl.enadeag.carchargeplanner.desktop.system.StatusDot;
import nl.enadeag.carchargeplanner.desktop.system.StatusIcon;
import nl.enadeag.carchargeplanner.desktop.system.Tray;
import nl.enadeag.carchargeplanner.desktop.system.TrayStatus;
import java.lang.System.Logger.Level;
import java.nio.file.Path;
import java.util.List;
import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.interfaces.DBus;

/**
 * The tray icon on Linux: our own StatusNotifierItem over D-Bus. AWT's tray is not used here: on Wayland it shows a
 * grey square that ignores clicks.
 *
 * <p>The icon goes to the panel twice: by name from an {@link IconThemeFolder}, which KDE Plasma colours to suit its
 * panel, and as pixels in several sizes for panels that cannot find it by name.
 *
 * <p>When the panel restarts, such as after a crash of Plasma's shell, its new StatusNotifierWatcher knows no items:
 * the tray registers again as soon as the watcher's name has a new owner.
 */
public final class StatusNotifierTray implements Tray {

    private static final System.Logger LOG = System.getLogger(StatusNotifierTray.class.getName());
    /** The sizes the panel can choose from, so it never has to scale the icon up. */
    private static final List<Integer> SIZES = List.of(16, 22, 24, 32, 48, 64);


    private final BusConnector connector;
    private final StatusIcon icon;
    private final IconThemeFolder theme;
    private final String id;
    private final String title;
    // the name the specification asks for: one per process and item
    private final String busName = "org.kde.StatusNotifierItem-" + ProcessHandle.current().pid() + "-1";

    private TrayStatus status = new TrayStatus(StatusDot.NONE, "");
    private MenuLabels labels;
    private DBusConnection bus;
    private TrayItem item;
    private TrayMenu menu;

    /**
     * Makes the tray; nothing appears until {@link #show(TrayActions)}, and its menu needs
     * {@link #showLabels(MenuLabels)} first.
     *
     * @param connector opens the session bus connection the icon is served on
     * @param icon draws the icon with its status dot, in every size the panel may need
     * @param iconFolder a folder of the tray's own that only this user may use, where it writes its icons for the
     *     panel; {@link #remove()} removes it again
     * @param id the app's ID, by which the panel knows the icon from one run to the next, such as to keep its place
     * @param title the name the panel shows for the icon, such as in its tooltip
     */
    public StatusNotifierTray(BusConnector connector, StatusIcon icon, Path iconFolder, String id, String title) {
        this.connector = connector;
        this.icon = icon;
        this.theme = new IconThemeFolder(iconFolder);
        this.id = id;
        this.title = title;
    }

    /**
     * {@inheritDoc}
     *
     * @throws IllegalStateException when the menu has no labels yet: {@link #showLabels(MenuLabels)} comes first
     */
    @Override
    public synchronized void show(TrayActions actions) throws TrayUnavailable {
        if (labels == null) {
            throw new IllegalStateException("the menu needs its labels before the tray shows");
        }
        theme.write(icon);
        item = new TrayItem(id, title, theme.path(), where -> apart(() -> actions.clicked().accept(where)));
        update();
        menu = new TrayMenu(entryLabels(), List.of(() -> apart(actions.open()), () -> apart(actions.settings()),
                () -> apart(actions.about()), () -> apart(actions.quit())));
        try {
            bus = connector.connect();
            bus.exportObject(item);
            bus.exportObject(menu);
            bus.requestBusName(busName);
            bus.addSigHandler(DBus.NameOwnerChanged.class, this::watcherChanged);
            register(bus);
        } catch (DBusException | RuntimeException e) {
            // no session bus, or no StatusNotifierWatcher (GNOME without the AppIndicator extension)
            remove();
            throw new TrayUnavailable("The desktop has no tray that this app can use", e);
        }
    }

    private void register(DBusConnection connection) throws DBusException {
        connection.getRemoteObject(StatusNotifierWatcher.BUS_NAME, StatusNotifierWatcher.OBJECT_PATH,
                StatusNotifierWatcher.class).RegisterStatusNotifierItem(busName);
    }

    /** Registers again with a watcher that took over the name: a new one knows nothing of the old one's items. */
    private void watcherChanged(DBus.NameOwnerChanged signal) {
        if (!StatusNotifierWatcher.BUS_NAME.equals(signal.name) || signal.newOwner.isEmpty()) {
            return;
        }
        // not on the connection's signal thread, which a call to the watcher would hold up
        apart(this::registerAgain);
    }

    private synchronized void registerAgain() {
        if (bus == null || !bus.isConnected()) {
            return;
        }
        try {
            register(bus);
            LOG.log(Level.INFO, "registered the tray icon with the restarted panel");
        } catch (DBusException | RuntimeException e) {
            // dbus-java reports a watcher that left again or answers with an error as a runtime exception
            LOG.log(Level.WARNING, "cannot register the tray icon with the restarted panel", e);
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

    /** Gives the menu new labels and tells the panel to read its layout again; its tooltip comes with the status. */
    @Override
    public synchronized void showLabels(MenuLabels labels) {
        this.labels = labels;
        if (menu == null || bus == null || !bus.isConnected()) {
            return;
        }
        var revision = menu.relabel(entryLabels());
        try {
            bus.sendMessage(new DbusMenu.LayoutUpdated(TrayMenu.OBJECT_PATH, revision, 0));
        } catch (DBusException e) {
            LOG.log(Level.WARNING, "cannot tell the panel that the tray menu changed", e);
        }
    }

    private List<String> entryLabels() {
        return List.of(labels.open(), labels.settings(), labels.about(), labels.quit());
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
