package io.github.enadeag.carchargeplanner.platform.linux;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.enadeag.carchargeplanner.desktop.system.AppIcon;
import io.github.enadeag.carchargeplanner.desktop.system.NotificationText;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
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
 * The notifications on a private D-Bus session of their own, with this test as the desktop's notification server:
 * nothing appears on the developer's desktop. The cases of the Python version's tests/test_notifier.py and
 * tests/test_notification_actions.py.
 */
@EnabledOnOs(OS.LINUX)
class FreedesktopNotificationsTest {

    private static final long TIMEOUT_S = 5;

    @TempDir
    Path folder;

    private IsolatedSessionBus bus;
    private String address;
    private DBusConnection desktop;
    private FakeServer server;
    private FreedesktopNotifications notifications;
    private final Semaphore opened = new Semaphore(0);
    private final AtomicReference<String> openLabel = new AtomicReference<>("Open dashboard");

    /** One call of Notify, as the server received it. */
    private record Call(String appName, long replacesId, String appIcon, String summary, String body,
            List<String> actions, Map<String, Variant<?>> hints, int expireTimeout) {
    }

    /** The desktop's side: it notes each notification and gives it the next id. */
    private static final class FakeServer implements NotificationServer {
        final BlockingQueue<Call> calls = new LinkedBlockingQueue<>();
        private long nextId = 40;

        @Override
        public synchronized UInt32 Notify(String appName, UInt32 replacesId, String appIcon, String summary, String body,
                List<String> actions, Map<String, Variant<?>> hints, int expireTimeout) {
            calls.add(new Call(appName, replacesId.longValue(), appIcon, summary, body, actions, hints, expireTimeout));
            return new UInt32(++nextId);
        }

        @Override
        public String getObjectPath() {
            return OBJECT_PATH;
        }
    }

    @BeforeEach
    void startAnIsolatedSessionBusWithANotificationServer() throws IOException, DBusException {
        bus = IsolatedSessionBus.start(folder);
        address = bus.address();
        desktop = DBusConnectionBuilder.forAddress(address).withShared(false).build();
        notifications = new FreedesktopNotifications(() -> DBusConnectionBuilder.forAddress(address).withShared(false).build(),
                "Car charge planner", "car-charge-planner", new AppIcon(), Files.createDirectory(folder.resolve("notify")),
                openLabel::get, Runnable::run);
    }

    @AfterEach
    void stopTheBus() {
        notifications.close();
        desktop.disconnect();
        bus.stop();
    }

    private void serve() throws DBusException {
        server = new FakeServer();
        desktop.exportObject(server);
        desktop.requestBusName(NotificationServer.BUS_NAME);
    }

    private long show() throws InterruptedException {
        notifications.show(new NotificationText("Charge window 10:00 – 15:00", "Set the current to 7 A"));
        assertThat(server.calls.poll(TIMEOUT_S, TimeUnit.SECONDS)).as("the server got the notification").isNotNull();
        return server.nextId;
    }

    private void click(long id, String key) throws DBusException {
        desktop.sendMessage(new NotificationServer.ActionInvoked(NotificationServer.OBJECT_PATH, new UInt32(id), key));
    }

    /**
     * How often the dashboard opened besides for a final click on a new notification. The signals are handled in the
     * order they were sent and each click's action runs as it is handled, so once the final click has opened it, every
     * click before has been handled.
     */
    private int opensAfterAFinalClickOnALaterNotification() throws Exception {
        var last = show();
        click(last, "default");
        assertThat(opened.tryAcquire(TIMEOUT_S, TimeUnit.SECONDS)).as("the final click").isTrue();
        return opened.drainPermits();
    }

    @Test
    void theCallCarriesTheAppNameIconTextActionsHintAndExpiry() throws Exception {
        serve();
        notifications.show(new NotificationText("Charge window 10:00 – 15:00", "Set the current to 7 A"));

        var call = server.calls.poll(TIMEOUT_S, TimeUnit.SECONDS);
        assertThat(call).isNotNull();
        assertThat(call.appName()).isEqualTo("Car charge planner");
        assertThat(call.replacesId()).isZero();
        assertThat(call.summary()).isEqualTo("Charge window 10:00 – 15:00");
        assertThat(call.body()).isEqualTo("Set the current to 7 A");
        // "default" is a click on the notification itself; "open" a button, for desktops that show buttons only
        assertThat(call.actions()).containsExactly("default", "Open dashboard", "open", "Open dashboard");
        assertThat(call.hints().get("desktop-entry").getValue()).isEqualTo("car-charge-planner");
        assertThat(call.expireTimeout()).isEqualTo(FreedesktopNotifications.EXPIRE_MS);
        var icon = Path.of(URI.create(call.appIcon()));
        assertThat(icon).startsWith(folder.resolve("notify"));
        assertThat(Files.readAllBytes(icon)).startsWith(0x89, 'P', 'N', 'G');
    }

    @Test
    void aClickOnOurNotificationOrItsButtonOpensTheDashboard() throws Exception {
        serve();
        notifications.listen(opened::release);
        var id = show();

        click(id, "default");
        assertThat(opened.tryAcquire(TIMEOUT_S, TimeUnit.SECONDS)).as("a click on the notification").isTrue();
        click(id, "open");
        assertThat(opened.tryAcquire(TIMEOUT_S, TimeUnit.SECONDS)).as("a click on its button").isTrue();
    }

    @Test
    void clicksOnOtherAppsNotificationsAndOtherActionsAreIgnored() throws Exception {
        serve();
        notifications.listen(opened::release);
        var id = show();

        click(999_999, "default");
        for (var key : List.of("dismiss", "snooze", "")) {
            click(id, key);
        }

        assertThat(opensAfterAFinalClickOnALaterNotification()).isZero();
    }

    @Test
    void onlyTheLatestNotificationsAreRecognised() throws Exception {
        serve();
        notifications.listen(opened::release);
        var first = show();
        for (int i = 0; i < FreedesktopNotifications.REMEMBER; i++) {
            show();
        }

        click(first, "default");

        assertThat(opensAfterAFinalClickOnALaterNotification()).isZero();
    }

    @Test
    void withoutANotificationServerTheAppGoesOnAndTheLogSaysSoOnce() {
        var warnings = warningsWhile(() -> {
            notifications.listen(opened::release);
            for (int i = 0; i < 3; i++) {
                notifications.show(new NotificationText("a", "b"));
            }
        });

        assertThat(warnings).hasSize(1).first().asString().startsWith("Could not show a notification");
    }

    @Test
    void theButtonOfTheNextNotificationSpeaksTheLanguageChosenSince() throws Exception {
        serve();
        show();

        openLabel.set("Dashboard openen");
        notifications.show(new NotificationText("Laadvenster 10:00 – 15:00", "Stel de stroom in op 7 A"));

        var call = server.calls.poll(TIMEOUT_S, TimeUnit.SECONDS);
        assertThat(call).isNotNull();
        assertThat(call.actions()).containsExactly("default", "Dashboard openen", "open", "Dashboard openen");
    }

    @Test
    void withoutASessionBusTheAppGoesOnAndTheLogSaysSoOnce() throws IOException {
        var offline = new FreedesktopNotifications(() -> {
            throw new DBusException("no session bus");
        }, "Car charge planner", "car-charge-planner", new AppIcon(), Files.createDirectory(folder.resolve("offline")),
                () -> "Open dashboard");

        var warnings = warningsWhile(() -> {
            offline.listen(opened::release);
            offline.show(new NotificationText("a", "b"));
            offline.show(new NotificationText("a", "b"));
        });
        offline.close();

        assertThat(warnings).hasSize(1);
    }

    @Test
    void theIconIsNotWrittenThroughALinkInItsFolder() throws Exception {
        var target = Files.writeString(folder.resolve("someone-elses.txt"), "keep");
        Files.createSymbolicLink(folder.resolve("notify").resolve("car-charge-planner.png"), target);
        serve();

        notifications.show(new NotificationText("a", "b"));

        var call = server.calls.poll(TIMEOUT_S, TimeUnit.SECONDS);
        assertThat(call).isNotNull();
        assertThat(call.appIcon()).isEqualTo(FreedesktopNotifications.FALLBACK_ICON);
        assertThat(Files.readString(target)).isEqualTo("keep");
    }

    @Test
    void closingRemovesTheIcon() throws Exception {
        serve();
        show();
        assertThat(folder.resolve("notify")).isNotEmptyDirectory();

        notifications.close();

        assertThat(folder.resolve("notify")).doesNotExist();
    }

    private static List<String> warningsWhile(Runnable action) {
        var warnings = new ArrayList<String>();
        var logger = Logger.getLogger(FreedesktopNotifications.class.getName());
        var handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                warnings.add(record.getMessage());
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        logger.addHandler(handler);
        try {
            action.run();
        } finally {
            logger.removeHandler(handler);
        }
        return warnings;
    }
}
