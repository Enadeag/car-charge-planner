package io.github.enadeag.carchargeplanner.desktop;

import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

/** The dashboard in a window of its own: in window mode, or the popup detached. Closing it hides it. */
final class DashboardWindow {

    private final Stage stage = new Stage();
    private final StackPane content = new StackPane();

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

    void show(DashboardView view) {
        view.setDetachable(false);
        content.getChildren().setAll(view);
        stage.show();
        stage.sizeToScene();
        stage.setIconified(false);
        stage.toFront();
        stage.requestFocus();
    }

    boolean isShowing() {
        return stage.isShowing();
    }

    /** Fits the window to new content, such as the first plan after "Fetching the forecast". */
    void fit() {
        if (stage.isShowing()) {
            stage.sizeToScene();
        }
    }

    void onClose(Runnable action) {
        stage.setOnCloseRequest(event -> action.run());
    }
}
