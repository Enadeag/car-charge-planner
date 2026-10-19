package nl.enadeag.carchargeplanner.platform.linux;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import nl.enadeag.carchargeplanner.desktop.system.PanelShade;
import nl.enadeag.carchargeplanner.desktop.system.StatusDot;
import nl.enadeag.carchargeplanner.desktop.system.StatusIcon;
import nl.enadeag.carchargeplanner.desktop.system.Tray;
import nl.enadeag.carchargeplanner.desktop.system.TrayStatus;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
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
import org.freedesktop.dbus.matchrules.DBusMatchRule;
import org.freedesktop.dbus.matchrules.DBusMatchRuleBuilder;
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

    private IsolatedSessionBus bus;
    private String address;
    private DBusConnection panel;
    private StatusNotifierTray tray;
    private final CountDownLatch opened = new CountDownLatch(1);
    private final CountDownLatch quit = new CountDownLatch(1);
    private final CountDownLatch settings = new CountDownLatch(1);
    private final CountDownLatch about = new CountDownLatch(1);
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
    void startAnIsolatedSessionBus() throws IOException, DBusException {
        bus = IsolatedSessionBus.start(folder);
        address = bus.address();
        panel = DBusConnectionBuilder.forAddress(address).withShared(false).build();
        tray = new StatusNotifierTray(() -> DBusConnectionBuilder.forAddress(address).withShared(false).build(),
                new StatusIcon(), folder.resolve("tray"), "car-charge-planner", "Car charge planner");
        tray.setMenuLabels(new Tray.MenuLabels("Open dashboard", "Settings…", "About…", "Quit"));
    }

    @AfterEach
    void stopTheBus() {
        if (bus == null) {
            return; // the test was skipped: there is no dbus-daemon
        }
        tray.remove();
        panel.disconnect();
        bus.stop();
    }

    private String showWithAWatcher() throws Exception {
        var watcher = new Watcher();
        panel.exportObject(watcher);
        panel.requestBusName(StatusNotifierWatcher.BUS_NAME);
        tray.add(new Tray.Actions(clicks::add, opened::countDown, settings::countDown, about::countDown,
                quit::countDown));
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
        assertThat(properties.get("Id").getValue()).isEqualTo("car-charge-planner");
        assertThat(properties.get("IconPixmap").getSig()).isEqualTo("a(iiay)");
        assertThat((List<?>) properties.get("IconPixmap").getValue()).hasSize(6);
    }

    private static DBusMatchRule signalRule(String iface, String member) throws DBusException {
        return DBusMatchRuleBuilder.create().withType("signal").withInterface(iface).withMember(member).build();
    }

    private Map<String, Variant<?>> properties(String name) throws DBusException {
        return panel.getRemoteObject(name, TrayItem.OBJECT_PATH, Properties.class).GetAll("org.kde.StatusNotifierItem");
    }

    /** The SVG the panel finds for the item's icon name in the item's icon theme folder. */
    private static String themeIcon(Map<String, Variant<?>> properties) throws IOException {
        var theme = Path.of((String) properties.get("IconThemePath").getValue());
        return Files.readString(theme.resolve("hicolor/scalable/apps/" + properties.get("IconName").getValue() + ".svg"));
    }

    private static String toolTipText(Map<String, Variant<?>> properties) {
        // a struct arrives as its fields: icon name, icon, title, text
        return (String) ((Object[]) properties.get("ToolTip").getValue())[3];
    }

    @Test
    void theIconShowsTheStatusGivenBeforeItAppearedByNameAndInTheTooltip() throws Exception {
        tray.setStatus(new TrayStatus(StatusDot.SOLAR, "Charge on sun now, until 14:30, at 10 A."));
        var properties = properties(showWithAWatcher());

        assertThat(themeIcon(properties)).isEqualTo(new StatusIcon().svg(StatusDot.SOLAR, PanelShade.DARK));
        // icon themes shorten an unknown name at its dashes: Plasma showed the system's "car" icon instead of ours
        assertThat((String) properties.get("IconName").getValue()).doesNotContain("-");
        // Plasma looks in IconThemePath only when its last folder is called "icons"
        assertThat(Path.of((String) properties.get("IconThemePath").getValue()).getFileName()).hasToString("icons");
        assertThat(toolTipText(properties)).isEqualTo("Charge on sun now, until 14:30, at 10 A.");
    }

    @Test
    void aNewStatusChangesTheIconAndTooltipAndTellsThePanel() throws Exception {
        var name = showWithAWatcher();
        var before = properties(name);
        var newIcon = new CountDownLatch(1);
        var newToolTip = new CountDownLatch(1);
        // generic handlers: dbus-java would look for the signals' classes in StatusNotifierItem, by the interface's name
        panel.addGenericSigHandler(signalRule("org.kde.StatusNotifierItem", "NewIcon"),
                _ -> newIcon.countDown());
        panel.addGenericSigHandler(signalRule("org.kde.StatusNotifierItem", "NewToolTip"),
                _ -> newToolTip.countDown());

        tray.setStatus(new TrayStatus(StatusDot.GRID, "Charge 8.4 kWh from the grid: Sunday 09:00 – 15:10."));

        assertThat(newIcon.await(TIMEOUT_S, TimeUnit.SECONDS)).as("NewIcon").isTrue();
        assertThat(newToolTip.await(TIMEOUT_S, TimeUnit.SECONDS)).as("NewToolTip").isTrue();
        var after = properties(name);
        assertThat(themeIcon(after)).isEqualTo(new StatusIcon().svg(StatusDot.GRID, PanelShade.DARK));
        assertThat(after.get("IconPixmap").getValue()).isNotEqualTo(before.get("IconPixmap").getValue());
        assertThat(toolTipText(after)).isEqualTo("Charge 8.4 kWh from the grid: Sunday 09:00 – 15:10.");
    }

    @Test
    void removingTheTrayRemovesItsIcons() throws Exception {
        showWithAWatcher();

        tray.remove();

        assertThat(folder.resolve("tray")).doesNotExist();
    }

    @Test
    void theMenuOffersOpenSettingsAboutAndQuit() throws Exception {
        var name = showWithAWatcher();
        var menu = panel.getRemoteObject(name, TrayMenu.OBJECT_PATH, DbusMenu.class);

        var entries = menu.GetGroupProperties(List.of(1, 2, 3, 4), List.of());
        assertThat(entries).extracting(entry -> (Object) entry.properties.get("label").getValue())
                .containsExactly("Open dashboard", "Settings…", "About…", "Quit");

        menu.Event(4, "clicked", new Variant<>(0), new UInt32(0));
        assertThat(quit.await(TIMEOUT_S, TimeUnit.SECONDS)).isTrue();
        menu.Event(3, "clicked", new Variant<>(0), new UInt32(0));
        assertThat(about.await(TIMEOUT_S, TimeUnit.SECONDS)).isTrue();
        menu.Event(2, "clicked", new Variant<>(0), new UInt32(0));
        assertThat(settings.await(TIMEOUT_S, TimeUnit.SECONDS)).isTrue();
        menu.Event(1, "clicked", new Variant<>(0), new UInt32(0));
        assertThat(opened.await(TIMEOUT_S, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void newLabelsReachTheShownMenuWithANewRevisionAndTheClicksStillWork() throws Exception {
        var name = showWithAWatcher();
        var menu = panel.getRemoteObject(name, TrayMenu.OBJECT_PATH, DbusMenu.class);
        var revisionBefore = menu.GetLayout(0, -1, List.of()).first;
        var updated = new LinkedBlockingQueue<Object[]>();
        panel.addGenericSigHandler(signalRule("com.canonical.dbusmenu", "LayoutUpdated"),
                signal -> {
                    try {
                        updated.add(signal.getParameters());
                    } catch (DBusException e) {
                        throw new IllegalStateException(e);
                    }
                });

        tray.setMenuLabels(new Tray.MenuLabels("Dashboard openen", "Instellingen…", "Over…", "Afsluiten"));

        var signal = updated.poll(TIMEOUT_S, TimeUnit.SECONDS);
        assertThat(signal).as("LayoutUpdated").isNotNull();
        var layout = menu.GetLayout(0, -1, List.of());
        assertThat(layout.first.longValue()).isGreaterThan(revisionBefore.longValue());
        assertThat(signal[0]).isEqualTo(layout.first);
        assertThat(signal[1]).isEqualTo(0);
        assertThat(menu.GetGroupProperties(List.of(1, 2, 3, 4), List.of()))
                .extracting(entry -> (Object) entry.properties.get("label").getValue())
                .containsExactly("Dashboard openen", "Instellingen…", "Over…", "Afsluiten");
        menu.Event(4, "clicked", new Variant<>(0), new UInt32(0));
        assertThat(quit.await(TIMEOUT_S, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void quittingFromTheMenuCanRemoveTheIcon() throws Exception {
        var watcher = new Watcher();
        panel.exportObject(watcher);
        panel.requestBusName(StatusNotifierWatcher.BUS_NAME);
        var removed = new CountDownLatch(1);
        tray.add(new Tray.Actions(clicks::add, opened::countDown, settings::countDown, about::countDown, () -> {
            tray.remove();
            removed.countDown();
        }));
        var name = watcher.registered.poll(TIMEOUT_S, TimeUnit.SECONDS);
        var bus = panel.getRemoteObject("org.freedesktop.DBus", "/org/freedesktop/DBus", DBus.class);
        assertThat(bus.NameHasOwner(name)).isTrue();

        panel.getRemoteObject(name, TrayMenu.OBJECT_PATH, DbusMenu.class).Event(4, "clicked", new Variant<>(0), new UInt32(0));

        assertThat(removed.await(TIMEOUT_S, TimeUnit.SECONDS)).as("the tray was removed").isTrue();

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_S);
        while (bus.NameHasOwner(name) && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        assertThat(bus.NameHasOwner(name)).isFalse();
    }

    @Test
    void theItemRegistersAgainWithAPanelThatRestarted() throws Exception {
        var name = showWithAWatcher();

        panel.disconnect();
        panel = DBusConnectionBuilder.forAddress(address).withShared(false).build();
        var restarted = new Watcher();
        panel.exportObject(restarted);
        panel.requestBusName(StatusNotifierWatcher.BUS_NAME);

        assertThat(restarted.registered.poll(TIMEOUT_S, TimeUnit.SECONDS)).isEqualTo(name);
    }

    @Test
    void withoutAWatcherThereIsNoTray() {
        var actions = new Tray.Actions(clicks::add, opened::countDown, settings::countDown, about::countDown,
                quit::countDown);
        assertThatThrownBy(() -> tray.add(actions)).isInstanceOf(Tray.TrayUnavailable.class);
    }
}
