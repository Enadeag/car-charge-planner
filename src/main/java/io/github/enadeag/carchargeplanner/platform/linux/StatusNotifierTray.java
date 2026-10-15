package io.github.enadeag.carchargeplanner.platform.linux;

import io.github.enadeag.carchargeplanner.desktop.AppIcon;
import io.github.enadeag.carchargeplanner.desktop.Tray;
import java.util.List;
import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.exceptions.DBusException;

/**
 * The tray icon on Linux: our own StatusNotifierItem over D-Bus. AWT's tray is not used here: on Wayland it shows a
 * grey square that ignores clicks.
 */
public final class StatusNotifierTray implements Tray {

    /** The sizes the panel can choose from, so it never has to scale the icon up. */
    private static final List<Integer> SIZES = List.of(16, 22, 24, 32, 48, 64);

    /** Opens a connection of the tray's own (not a shared one: {@link #remove()} closes it); tests use a private bus. */
    public interface BusConnector {
        DBusConnection connect() throws DBusException;
    }

    private final BusConnector connector;
    private final AppIcon icon;
    private final String title;
    // the name the specification asks for: one per process and item
    private final String busName = "org.kde.StatusNotifierItem-" + ProcessHandle.current().pid() + "-1";

    private DBusConnection bus;

    public StatusNotifierTray(BusConnector connector, AppIcon icon, String title) {
        this.connector = connector;
        this.icon = icon;
        this.title = title;
    }

    @Override
    public void show(TrayActions actions) throws TrayUnavailable {
        var images = SIZES.stream().map(icon::image).toList();
        var item = new TrayItem("car-charge-planner", title, images, apart(actions.open()));
        var menu = new TrayMenu(List.of(
                new TrayMenu.Entry("Open dashboard", apart(actions.open())),
                new TrayMenu.Entry("Quit", apart(actions.quit()))));
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
    private static Runnable apart(Runnable action) {
        return () -> Thread.ofVirtual().name("tray-action").start(action);
    }

    @Override
    public void remove() {
        if (bus != null) {
            // the panel drops the icon as soon as our name leaves the bus
            bus.disconnect();
        }
    }
}
