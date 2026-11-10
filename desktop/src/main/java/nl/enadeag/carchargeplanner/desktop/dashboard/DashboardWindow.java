package nl.enadeag.carchargeplanner.desktop.dashboard;

import nl.enadeag.carchargeplanner.desktop.system.AppIcon;
import nl.enadeag.carchargeplanner.desktop.theme.WindowTheme;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.StackPane;
import javafx.stage.Screen;
import javafx.stage.Stage;

/**
 * Closing it hides it, and so does Escape while the tray can bring it back; Escape in an open entry, such as the
 * battery level's, closes only the entry.
 */
final class DashboardWindow {

    private final Stage stage = new Stage();
    private final StackPane content = new StackPane();
    /** False once closing quits the app: Escape is too easy a key for that. */
    private boolean escapeHides = true;

    DashboardWindow(WindowTheme theme, AppIcon icon, String appName) {
        var scene = new Scene(content);
        theme.style(scene);
        stage.setScene(scene);
        stage.setTitle(appName);
        stage.getIcons().setAll(icon.fxImages());
        stage.setOnCloseRequest(event -> {
            event.consume();
            stage.hide();
        });
        scene.addEventHandler(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ESCAPE && escapeHides) {
                stage.hide();
                event.consume();
            }
        });
    }

    void show(DashboardView view) {
        view.setDetachable(false);
        content.getChildren().setAll(view);
        stage.show();
        fit();
        stage.setIconified(false);
        stage.toFront();
        stage.requestFocus();
    }

    /**
     * The window stays open or closed, and fits the new view.
     *
     * @param after the view to show in its place; nothing changes when the window does not hold {@code before}
     */
    void replace(DashboardView before, DashboardView after) {
        if (content.getChildren().contains(before)) {
            after.setDetachable(false);
            content.getChildren().setAll(after);
            fit();
        }
    }

    boolean isShowing() {
        return stage.isShowing();
    }

    /**
     * Fits the window to new content, such as another day or the first plan after "Fetching the forecast", at most
     * as tall as the screen's free area (the day scrolls inside).
     */
    void fit() {
        if (!stage.isShowing()) {
            return;
        }
        stage.sizeToScene();
        var area = Screen.getScreensForRectangle(stage.getX(), stage.getY(), stage.getWidth(), 1).stream()
                .findFirst().orElse(Screen.getPrimary()).getVisualBounds();
        // under XWayland JavaFX can leave out the title bar when it sizes to the scene; add it as it is now
        double decorations = stage.getHeight() - stage.getScene().getHeight();
        double needed = content.prefHeight(stage.getScene().getWidth()) + decorations;
        stage.setHeight(Math.min(needed, area.getHeight()));
        if (stage.getY() + stage.getHeight() > area.getMaxY()) {
            stage.setY(Math.max(area.getMinY(), area.getMaxY() - stage.getHeight()));
        }
    }

    /**
     * Replaces hiding the window on close by another action; Escape then no longer closes it.
     *
     * @param action what closing the window does, such as quitting the app; called on the JavaFX thread
     */
    void onClose(Runnable action) {
        escapeHides = false;
        stage.setOnCloseRequest(_ -> action.run());
    }
}
