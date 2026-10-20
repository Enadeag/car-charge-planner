package nl.enadeag.carchargeplanner.platform.linux;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.freedesktop.dbus.DBusPath;
import org.freedesktop.dbus.connections.base.AbstractConnectionBase;
import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.types.UInt32;
import org.freedesktop.dbus.types.Variant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * Start at login through a fake Background portal on a private D-Bus, which answers as xdg-desktop-portal does: on
 * the request object named after the caller and its token, with how the owner answered.
 */
@EnabledOnOs(OS.LINUX)
class PortalStartAtLoginTest {

    private static final Duration WAIT = Duration.ofSeconds(10);
    private static final List<String> COMMAND = List.of("car-charge-planner", "--background");

    @TempDir
    Path folder;

    private IsolatedSessionBus bus;
    private DBusConnection desktop;

    /** One call the portal was given. */
    private record Asked(String parentWindow, Map<String, Variant<?>> options) {
    }

    /** The desktop's side: answers each request as the test says, and notes what it was asked. */
    private final class FakePortal implements BackgroundPortal.Background {
        final BlockingQueue<Asked> asked = new LinkedBlockingQueue<>();
        /** 0 granted, 1 cancelled, 2 otherwise; null to give no answer. */
        volatile Long response = 0L;
        /** Whether the desktop grants start at login; null to leave it out of the results. */
        volatile Boolean autostart = true;
        /** A request path of the portal's own, as old portals give, instead of the one named after the token. */
        volatile String ownPath;

        @Override
        public DBusPath RequestBackground(String parentWindow, Map<String, Variant<?>> options) {
            asked.add(new Asked(parentWindow, options));
            var sender = AbstractConnectionBase.getCallInfo().getSource().substring(1).replace('.', '_');
            var path = ownPath != null ? ownPath
                    : BackgroundPortal.REQUEST_FOLDER + sender + "/" + options.get("handle_token").getValue();
            var code = response;
            if (code != null) {
                var results = autostart == null ? Map.<String, Variant<?>>of()
                        : Map.<String, Variant<?>>of("background", new Variant<>(true),
                                "autostart", new Variant<>(autostart));
                try {
                    desktop.sendMessage(new BackgroundPortal.Request.Response(path, new UInt32(code), results));
                } catch (DBusException e) {
                    throw new IllegalStateException(e);
                }
            }
            return new DBusPath(path);
        }

        @Override
        public String getObjectPath() {
            return BackgroundPortal.OBJECT_PATH;
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
        desktop.requestBusName(BackgroundPortal.BUS_NAME);
        return portal;
    }

    private Path choice() {
        return folder.resolve("config/car-charge-planner/start-at-login");
    }

    private PortalStartAtLogin startAtLogin(Duration wait) {
        return new PortalStartAtLogin(this::connect, choice(), COMMAND, wait);
    }

    @Test
    void switchedOnThePortalIsAskedToStartTheCommandAtLoginAndTheChoiceIsRemembered() throws Exception {
        var portal = serve();
        var startAtLogin = startAtLogin(WAIT);
        assertThat(startAtLogin.isOn()).isFalse();

        startAtLogin.switchOn();

        var asked = portal.asked.poll(WAIT.toSeconds(), TimeUnit.SECONDS);
        assertThat(asked).isNotNull();
        assertThat(asked.parentWindow()).isEmpty();
        assertThat(asked.options().get("autostart").getValue()).isEqualTo(true);
        assertThat(asked.options().get("commandline").getValue()).isEqualTo(COMMAND);
        assertThat(startAtLogin.isOn()).isTrue();
        assertThat(choice()).exists();
    }

    @Test
    void switchedOffThePortalIsAskedToStopItAndTheChoiceIsForgotten() throws Exception {
        var portal = serve();
        var startAtLogin = startAtLogin(WAIT);
        startAtLogin.switchOn();
        portal.autostart = false;

        startAtLogin.switchOff();

        portal.asked.take();
        assertThat(portal.asked.take().options().get("autostart").getValue()).isEqualTo(false);
        assertThat(startAtLogin.isOn()).isFalse();
        assertThat(choice()).doesNotExist();
    }

    @Test
    void anOwnerWhoSaysNoLeavesItOff() throws Exception {
        serve().response = 1L;
        var startAtLogin = startAtLogin(WAIT);

        assertThatThrownBy(startAtLogin::switchOn).isInstanceOf(IOException.class)
                .hasMessage("the request was cancelled");
        assertThat(startAtLogin.isOn()).isFalse();
    }

    @Test
    void aDesktopThatEndsTheRequestOtherwiseLeavesItAsItWas() throws Exception {
        var portal = serve();
        var startAtLogin = startAtLogin(WAIT);
        startAtLogin.switchOn();
        portal.response = 2L;

        assertThatThrownBy(startAtLogin::switchOff).isInstanceOf(IOException.class)
                .hasMessage("the desktop refused it (response 2)");
        assertThat(startAtLogin.isOn()).isTrue();
    }

    @Test
    void aGrantWithoutStartAtLoginLeavesItOff() throws Exception {
        var portal = serve();
        portal.autostart = false;

        assertThatThrownBy(startAtLogin(WAIT)::switchOn).isInstanceOf(IOException.class)
                .hasMessage("the desktop did not allow the app to start at login");
        portal.autostart = null;
        assertThatThrownBy(startAtLogin(WAIT)::switchOn).isInstanceOf(IOException.class);
        assertThat(choice()).doesNotExist();
    }

    @Test
    void anAnswerOnTheRequestObjectThePortalNamedCountsToo() throws Exception {
        serve().ownPath = BackgroundPortal.REQUEST_FOLDER + "old/1";

        startAtLogin(WAIT).switchOn();

        assertThat(choice()).exists();
    }

    @Test
    void anAnswerThatDoesNotComeInTimeLeavesItOff() throws Exception {
        serve().response = null;

        assertThatThrownBy(startAtLogin(Duration.ofMillis(300))::switchOn).isInstanceOf(IOException.class)
                .hasMessage("the desktop did not answer in time");
        assertThat(choice()).doesNotExist();
    }

    @Test
    void withoutAPortalItSaysSo() {
        assertThatThrownBy(startAtLogin(WAIT)::switchOn).isInstanceOf(IOException.class)
                .hasMessageStartingWith("the desktop's Background portal cannot be reached");
        assertThat(choice()).doesNotExist();
    }

    @Test
    void theWaitMustBeMoreThanZero() {
        assertThatThrownBy(() -> startAtLogin(Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
    }
}
