package nl.enadeag.carchargeplanner.platform.linux;

import static org.assertj.core.api.Assertions.assertThat;

import nl.enadeag.carchargeplanner.desktop.system.ClockFormat;
import java.nio.file.Path;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.exceptions.DBusExecutionException;
import org.freedesktop.dbus.interfaces.DBus;
import org.freedesktop.dbus.types.Variant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/** GNOME's clock format through a fake Settings portal on a private D-Bus, which answers as xdg-desktop-portal does. */
@EnabledOnOs(OS.LINUX)
class PortalClockTest {

    /** What the environment's time locale says, followed when the portal cannot answer. */
    private static final ClockFormat OTHERWISE = ClockFormat.TWENTY_FOUR_HOUR;

    @TempDir
    Path folder;

    private IsolatedSessionBus bus;
    private DBusConnection desktop;
    private final BlockingQueue<ClockFormat> clocks = new LinkedBlockingQueue<>();

    /** The desktop's side: holds GNOME's interface settings, of which the clock format. */
    private static final class FakePortal implements SettingsPortal {
        /** The clock format, such as "12h"; null for a desktop without the setting. */
        volatile String clockFormat = "24h";
        /** False for a portal older than version 2 of the interface, which has only Read. */
        volatile boolean readOne = true;

        private Variant<?> value(String namespace, String key) {
            if (!namespace.equals(PortalClock.NAMESPACE) || !key.equals(PortalClock.KEY) || clockFormat == null) {
                throw new DBusExecutionException("Requested setting not found");
            }
            return new Variant<>(clockFormat);
        }

        @Override
        public Variant<?> ReadOne(String namespace, String key) {
            if (!readOne) {
                throw new DBusExecutionException("No such method ReadOne");
            }
            return value(namespace, key);
        }

        @Override
        public Variant<?> Read(String namespace, String key) {
            // as xdg-desktop-portal does, for the programs written against its first version
            return new Variant<>(value(namespace, key), "v");
        }

        @Override
        public String getObjectPath() {
            return SettingsPortal.OBJECT_PATH;
        }
    }

    @BeforeEach
    void startAnIsolatedSessionBus() throws Exception {
        bus = IsolatedSessionBus.start(folder);
        desktop = connect();
    }

    @AfterEach
    void stopTheBus() {
        if (bus == null) {
            return; // the test was skipped: there is no dbus-daemon
        }
        desktop.disconnect();
        bus.stop();
    }

    private DBusConnection connect() throws DBusException {
        return DBusConnectionBuilder.forAddress(bus.address()).withShared(false).build();
    }

    private FakePortal serve() throws DBusException {
        var portal = new FakePortal();
        desktop.exportObject(portal);
        desktop.requestBusName(SettingsPortal.BUS_NAME);
        return portal;
    }

    private void follow() {
        new PortalClock(this::connect, changed -> changed.accept(OTHERWISE)).follow(clocks::add);
    }

    private void change(String namespace, String key, String value) throws DBusException {
        desktop.sendMessage(new SettingsPortal.SettingChanged(SettingsPortal.OBJECT_PATH, namespace, key,
                new Variant<>(value)));
    }

    /** Whether the first connection that joined the bus, of these changes of owner, left it again within a while. */
    private static boolean joinedAndLeft(BlockingQueue<DBus.NameOwnerChanged> owners) throws InterruptedException {
        String joined = null;
        for (var change = owners.poll(10, TimeUnit.SECONDS); change != null;
                change = owners.poll(10, TimeUnit.SECONDS)) {
            if (joined == null && change.name.startsWith(":") && change.oldOwner.isEmpty()) {
                joined = change.name;
            } else if (change.name.equals(joined) && change.newOwner.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** The next clock given, or null when none came within a while. */
    private ClockFormat next() throws InterruptedException {
        return clocks.poll(10, TimeUnit.SECONDS);
    }

    @Test
    void gnomesClockFormatIsGivenAtOnce() throws Exception {
        serve().clockFormat = "12h";

        follow();

        assertThat(clocks).containsExactly(ClockFormat.TWELVE_HOUR);
    }

    @Test
    void anOlderPortalIsReadThroughItsFirstMethod() throws Exception {
        var portal = serve();
        portal.readOne = false;
        portal.clockFormat = "12h";

        follow();

        assertThat(clocks).containsExactly(ClockFormat.TWELVE_HOUR);
    }

    @Test
    void aChangeWhileFollowingIsGivenAndOtherSettingsAreNot() throws Exception {
        serve();
        follow();
        assertThat(next()).isEqualTo(ClockFormat.TWENTY_FOUR_HOUR);

        change(PortalClock.NAMESPACE, "color-scheme", "prefer-dark");
        change("org.example.other", PortalClock.KEY, "12h");
        change(PortalClock.NAMESPACE, PortalClock.KEY, "12h");

        assertThat(next()).isEqualTo(ClockFormat.TWELVE_HOUR);
        change(PortalClock.NAMESPACE, PortalClock.KEY, "24h");
        assertThat(next()).isEqualTo(ClockFormat.TWENTY_FOUR_HOUR);
        assertThat(clocks).isEmpty();
    }

    @Test
    void aChangeFromAnotherProgramThanThePortalIsNotHeard() throws Exception {
        serve();
        follow();
        assertThat(next()).isEqualTo(ClockFormat.TWENTY_FOUR_HOUR);
        var other = connect();
        try {
            other.sendMessage(new SettingsPortal.SettingChanged(SettingsPortal.OBJECT_PATH, PortalClock.NAMESPACE,
                    PortalClock.KEY, new Variant<>("12h")));
            change(PortalClock.NAMESPACE, PortalClock.KEY, "36h");

            assertThat(next()).isEqualTo(ClockFormat.LANGUAGE);
            assertThat(clocks).isEmpty();
        } finally {
            other.disconnect();
        }
    }

    @Test
    void aValueTheAppDoesNotKnowLeavesTheClockToTheLanguage() throws Exception {
        serve().clockFormat = "36h";

        follow();

        assertThat(clocks).containsExactly(ClockFormat.LANGUAGE);
    }

    @Test
    void withoutTheSettingTheOtherSettingIsFollowed() throws Exception {
        serve().clockFormat = null;
        var owners = new LinkedBlockingQueue<DBus.NameOwnerChanged>();
        desktop.addSigHandler(DBus.NameOwnerChanged.class, owners::add);

        follow();

        assertThat(clocks).containsExactly(OTHERWISE);
        assertThat(joinedAndLeft(owners)).as("the connection to the portal is closed").isTrue();
    }

    @Test
    void withoutAPortalTheOtherSettingIsFollowed() {
        follow();

        assertThat(clocks).containsExactly(OTHERWISE);
    }

    @Test
    void withoutABusTheOtherSettingIsFollowed() {
        new PortalClock(() -> {
            throw new DBusException("no session bus");
        }, changed -> changed.accept(OTHERWISE)).follow(clocks::add);

        assertThat(clocks).containsExactly(OTHERWISE);
    }
}
