package io.github.enadeag.carchargeplanner.desktop;

import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Screen;
import javafx.stage.Stage;

/** The dashboard in a window of its own: in window mode, or the popup detached. Closing it hides it. */
final class DashboardWindow {

    private final Stage stage = new Stage();
    private final StackPane content = new StackPane();

    /**
     * Makes the window, hidden.
     *
     * @param theme makes the window light or dark as the system is
     * @param icon the app icon, for the title bar and the taskbar
     */
    DashboardWindow(SystemTheme theme, AppIcon icon) {
        var scene = new Scene(content);
        theme.style(scene);
        stage.setScene(scene);
        stage.setTitle("Car charge planner");
        stage.getIcons().setAll(icon.fxImages());
        stage.setOnCloseRequest(event -> {
            event.consume();
            stage.hide();
        });
    }

    /**
     * Shows the dashboard in the window, in front, sized to its content.
     *
     * @param view the dashboard to show; taken out of the popup if it is there
     */
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
     * Puts another view where one is shown, such as the same dashboard in another language. The window stays open or
     * closed, and fits the new view.
     *
     * @param before the view that may be in the window now
     * @param after the view to show in its place; nothing changes when the window does not hold {@code before}
     */
    void replace(DashboardView before, DashboardView after) {
        if (content.getChildren().contains(before)) {
            after.setDetachable(false);
            content.getChildren().setAll(after);
            fit();
        }
    }

    /**
     * Whether the window is open.
     *
     * @return true while it shows
     */
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
     * Replaces hiding the window on close by another action.
     *
     * @param action what closing the window does, such as quitting the app; called on the JavaFX thread
     */
    void onClose(Runnable action) {
        stage.setOnCloseRequest(event -> action.run());
    }
}
