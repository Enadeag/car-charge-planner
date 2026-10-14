package spike;

import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

/**
 * Question 1c: tray icon on KDE/GNOME via our own StatusNotifierItem over dbus-java.
 * Left click toggles a JavaFX window, right click shows the dbusmenu, the icon toggles a green
 * status dot every 5 s and the disabled "Status:" menu entry changes with it.
 */
public class SniTray {
    static Stage stage;
    static boolean dot;

    static void log(String s) {
        System.out.println(LocalTime.now().withNano(0) + " " + s);
        System.out.flush();
    }

    public static void main(String[] args) throws Exception {
        Platform.setImplicitExit(false);
        Platform.startup(() -> { });
        var sni = Sni.start("Car charge planner (SNI)", menu(false), SniTray::toggleWindow, id -> {
            switch (id) {
                case 1 -> toggleWindow();
                case 2 -> log("menu: toggle (handled by the timer)");
                case 5 -> System.exit(0);
                default -> log("menu id " + id);
            }
        }, SniTray::log);

        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(() -> {
            dot = !dot;
            sni.setDot(dot ? Icons.GREEN : null);
            sni.setLabels(menu(dot));
            log("icon changed, dot=" + dot);
        }, 5, 5, TimeUnit.SECONDS);
    }

    static List<String> menu(boolean dot) {
        return List.of("Open window", "Toggle status dot", dot ? "Status: charging" : "Status: idle", "-", "Quit");
    }

    static void toggleWindow() {
        Platform.runLater(() -> {
            if (stage == null) {
                stage = new Stage();
                stage.setTitle("Car charge planner (SNI tray spike)");
                var label = new Label("Opened from the StatusNotifierItem");
                label.setStyle("-fx-font-size: 18px;");
                stage.setScene(new Scene(new StackPane(label), 420, 200));
            }
            if (stage.isShowing() && stage.isFocused()) {
                stage.hide();
                log("window hidden");
            } else {
                stage.show();
                stage.toFront();
                stage.requestFocus();
                log("window shown, focused=" + stage.isFocused());
            }
        });
    }
}
