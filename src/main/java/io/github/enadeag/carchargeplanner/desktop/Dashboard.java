package io.github.enadeag.carchargeplanner.desktop;

import io.github.enadeag.carchargeplanner.engine.Outlook;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * The one screen that shows the plan. For now a window with the main card; closing it leaves the app in the tray.
 *
 * <p>Make it on the JavaFX thread; its methods may be called from any thread.
 */
public final class Dashboard {

    private final Stage stage = new Stage();
    private final MainCard mainCard = new MainCard();

    public Dashboard(AppIcon icon) {
        var title = new Label("CAR CHARGE PLANNER");
        title.getStyleClass().add("app-title");
        var page = new VBox(title, mainCard);
        page.getStyleClass().add("page");
        page.setPrefWidth(460);
        mainCard.show(MainCardText.loading());

        var scene = new Scene(page);
        scene.getStylesheets().add(Dashboard.class.getResource("dashboard.css").toExternalForm());
        SystemTheme.follow(scene);
        stage.setScene(scene);
        stage.setTitle("Car charge planner");
        stage.getIcons().setAll(icon.fxImages());
        stage.setOnCloseRequest(event -> {
            event.consume();
            stage.hide();
        });
    }

    public void open() {
        Platform.runLater(() -> {
            stage.show();
            stage.setIconified(false);
            stage.toFront();
            stage.requestFocus();
        });
    }

    public void show(Outlook outlook) {
        var text = MainCardText.of(outlook);
        Platform.runLater(() -> {
            mainCard.show(text);
            stage.sizeToScene(); // a window opened before the plan came is still the size of "Fetching the forecast"
        });
    }

    /** Without a tray, closing the window is the only way to quit. */
    public void quitWhenClosed(Runnable quit) {
        Platform.runLater(() -> stage.setOnCloseRequest(event -> quit.run()));
    }
}
