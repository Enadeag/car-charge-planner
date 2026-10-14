package spike;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.freedesktop.dbus.annotations.DBusInterfaceName;
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.interfaces.DBusInterface;
import org.freedesktop.dbus.messages.DBusSignal;
import org.freedesktop.dbus.types.UInt32;
import org.freedesktop.dbus.types.Variant;

/**
 * Question 3 (Linux): org.freedesktop.Notifications with actions over dbus-java. The window is
 * hidden first (as when the app lives in the tray); clicking the notification or its button shows
 * the window and tries to bring it to the front.
 */
public class LinuxNotify {

    @DBusInterfaceName("org.freedesktop.Notifications")
    public interface Notifications extends DBusInterface {
        UInt32 Notify(String appName, UInt32 replacesId, String appIcon, String summary, String body,
                      List<String> actions, Map<String, Variant<?>> hints, int expireTimeout);

        class ActionInvoked extends DBusSignal {
            public final UInt32 id;
            public final String actionKey;
            public ActionInvoked(String path, UInt32 id, String actionKey) throws DBusException {
                super(path, id, actionKey);
                this.id = id;
                this.actionKey = actionKey;
            }
        }

        class ActivationToken extends DBusSignal {
            public final UInt32 id;
            public final String token;
            public ActivationToken(String path, UInt32 id, String token) throws DBusException {
                super(path, id, token);
                this.id = id;
                this.token = token;
            }
        }

        class NotificationClosed extends DBusSignal {
            public final UInt32 id;
            public final UInt32 reason;
            public NotificationClosed(String path, UInt32 id, UInt32 reason) throws DBusException {
                super(path, id, reason);
                this.id = id;
                this.reason = reason;
            }
        }
    }

    static Stage stage;

    static void log(String s) {
        System.out.println(LocalTime.now().withNano(0) + " " + s);
        System.out.flush();
    }

    public static void main(String[] args) throws Exception {
        Platform.setImplicitExit(false);
        Platform.startup(() -> {
            stage = new Stage();
            stage.setTitle("Car charge planner (notification spike)");
            var label = new Label("Brought up by clicking the notification");
            label.setStyle("-fx-font-size: 18px;");
            stage.setScene(new Scene(new StackPane(label), 460, 200));
        });

        var conn = DBusConnectionBuilder.forSessionBus().build();
        var n = conn.getRemoteObject("org.freedesktop.Notifications", "/org/freedesktop/Notifications",
                Notifications.class);
        conn.addSigHandler(Notifications.ActivationToken.class, s -> log("ActivationToken id=" + s.id + " token=" + s.token));
        conn.addSigHandler(Notifications.NotificationClosed.class, s -> log("NotificationClosed id=" + s.id + " reason=" + s.reason));
        conn.addSigHandler(Notifications.ActionInvoked.class, s -> {
            log("ActionInvoked id=" + s.id + " action=" + s.actionKey);
            Platform.runLater(() -> {
                stage.show();
                stage.setIconified(false);
                stage.toFront();
                stage.requestFocus();
                log("window shown, focused=" + stage.isFocused());
            });
        });

        send(n);
        // Second round: put another app in front of the window, then click this one to test re-raising.
        Thread.sleep(15000);
        send(n);
    }

    static void send(Notifications n) {
        var hints = Map.<String, Variant<?>>of(
                "desktop-entry", new Variant<>("car-charge-planner-spike"),
                "urgency", new Variant<>((byte) 1));
        var id = n.Notify("Car charge planner", new UInt32(0), "battery-full-charging",
                "Charge window starts at 11:30",
                "About 18 kWh from the sun until 15:00. Plug in the car.",
                List.of("default", "Open", "open", "Open planner"), hints, 0);
        log("Notify sent, id=" + id + "; click it (body or 'Open planner') to bring the window up");
    }
}
