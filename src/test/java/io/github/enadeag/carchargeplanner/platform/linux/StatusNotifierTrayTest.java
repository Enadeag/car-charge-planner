package io.github.enadeag.carchargeplanner.platform.linux;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.github.enadeag.carchargeplanner.desktop.AppIcon;
import io.github.enadeag.carchargeplanner.desktop.Tray;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import javafx.geometry.Point2D;
import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.interfaces.DBus;
import org.freedesktop.dbus.interfaces.Properties;
import org.freedesktop.dbus.types.UInt32;
import org.freedesktop.dbus.types.Variant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * The tray item on a private D-Bus session of its own, with this test as the panel: nothing appears on the
 * developer's desktop.
 */
@EnabledOnOs(OS.LINUX)
class StatusNotifierTrayTest {

    private static final long TIMEOUT_S = 5;

    @TempDir
    Path folder;

    private Process busDaemon;
    private String address;
    private DBusConnection panel;
    private StatusNotifierTray tray;
    private final CountDownLatch opened = new CountDownLatch(1);
    private final CountDownLatch quit = new CountDownLatch(1);
    private final BlockingQueue<Point2D> clicks = new LinkedBlockingQueue<>();

    /** The panel's side of the protocol: it only notes which items register. */
    private static final class Watcher implements StatusNotifierWatcher {
        final BlockingQueue<String> registered = new LinkedBlockingQueue<>();

        @Override
        public void RegisterStatusNotifierItem(String service) {
            registered.add(service);
        }

        @Override
        public String getObjectPath() {
            return OBJECT_PATH;
        }
    }

    @BeforeEach
    void startAPrivateBus() throws IOException, DBusException {
        var socket = folder.resolve("bus");
        try {
            busDaemon = new ProcessBuilder("dbus-daemon", "--session", "--nofork", "--nopidfile",
                    "--address=unix:path=" + socket, "--print-address=1").start();
        } catch (IOException e) {
            assumeTrue(false, "dbus-daemon is not installed");
        }
        // it prints the address once it accepts connections
        address = new BufferedReader(new InputStreamReader(busDaemon.getInputStream())).readLine().strip();
        panel = DBusConnectionBuilder.forAddress(address).withShared(false).build();
        tray = new StatusNotifierTray(() -> DBusConnectionBuilder.forAddress(address).withShared(false).build(), new AppIcon(),
                "Car charge planner");
    }

    @AfterEach
    void stopTheBus() {
        tray.remove();
        panel.disconnect();
        busDaemon.destroy();
    }

    private String showWithAWatcher() throws Exception {
        var watcher = new Watcher();
        panel.exportObject(watcher);
        panel.requestBusName(StatusNotifierWatcher.BUS_NAME);
        tray.show(new Tray.TrayActions(clicks::add, opened::countDown, quit::countDown));
        var name = watcher.registered.poll(TIMEOUT_S, TimeUnit.SECONDS);
        assertThat(name).as("the item registered with the watcher").isNotNull();
        return name;
    }

    @Test
    void aClickOnTheIconSaysWhereTheIconIs() throws Exception {
        var name = showWithAWatcher();

        panel.getRemoteObject(name, TrayItem.OBJECT_PATH, StatusNotifierItem.class).Activate(1830, 1176);

        assertThat(clicks.poll(TIMEOUT_S, TimeUnit.SECONDS)).isEqualTo(new Point2D(1830, 1176));
        assertThat(opened.getCount()).isEqualTo(1);
        assertThat(quit.getCount()).isEqualTo(1);
    }

    @Test
    void theIconComesInEverySizeAsArgbPixels() throws Exception {
        var name = showWithAWatcher();

        var properties = panel.getRemoteObject(name, TrayItem.OBJECT_PATH, Properties.class)
                .GetAll("org.kde.StatusNotifierItem");

        assertThat(properties.get("Title").getValue()).isEqualTo("Car charge planner");
        assertThat(properties.get("IconPixmap").getSig()).isEqualTo("a(iiay)");
        assertThat((List<?>) properties.get("IconPixmap").getValue()).hasSize(6);
    }

    @Test
    void theMenuOffersOpenAndQuit() throws Exception {
        var name = showWithAWatcher();
        var menu = panel.getRemoteObject(name, TrayMenu.OBJECT_PATH, DbusMenu.class);

        var entries = menu.GetGroupProperties(List.of(1, 2), List.of());
        assertThat(entries).extracting(entry -> (Object) entry.properties.get("label").getValue())
                .containsExactly("Open dashboard", "Quit");

        menu.Event(2, "clicked", new Variant<>(0), new UInt32(0));
        assertThat(quit.await(TIMEOUT_S, TimeUnit.SECONDS)).isTrue();
        menu.Event(1, "clicked", new Variant<>(0), new UInt32(0));
        assertThat(opened.await(TIMEOUT_S, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void quittingFromTheMenuCanRemoveTheIcon() throws Exception {
        var watcher = new Watcher();
        panel.exportObject(watcher);
        panel.requestBusName(StatusNotifierWatcher.BUS_NAME);
        var removed = new CountDownLatch(1);
        tray.show(new Tray.TrayActions(clicks::add, opened::countDown, () -> {
            tray.remove();
            removed.countDown();
        }));
        var name = watcher.registered.poll(TIMEOUT_S, TimeUnit.SECONDS);
        var bus = panel.getRemoteObject("org.freedesktop.DBus", "/org/freedesktop/DBus", DBus.class);
        assertThat(bus.NameHasOwner(name)).isTrue();

        panel.getRemoteObject(name, TrayMenu.OBJECT_PATH, DbusMenu.class).Event(2, "clicked", new Variant<>(0), new UInt32(0));

        assertThat(removed.await(TIMEOUT_S, TimeUnit.SECONDS)).as("the tray was removed").isTrue();

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_S);
        while (bus.NameHasOwner(name) && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        assertThat(bus.NameHasOwner(name)).isFalse();
    }

    @Test
    void withoutAWatcherThereIsNoTray() {
        assertThatThrownBy(() -> tray.show(new Tray.TrayActions(clicks::add, opened::countDown, quit::countDown)))
                .isInstanceOf(Tray.TrayUnavailable.class);
    }
}
