package io.github.enadeag.carchargeplanner.desktop;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import javafx.application.Platform;
import javafx.geometry.Dimension2D;
import javafx.geometry.Point2D;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/** The dashboard as a popup next to the tray icon. A click elsewhere or Escape closes it. */
final class DashboardPopup {

    /**
     * A click on the tray icon while the popup is open first takes the focus away, which closes it, and only then
     * reaches us as a request to open: within this time after closing, that request is the same click.
     */
    private static final Duration SAME_CLICK = Duration.ofMillis(400);

    private final Stage stage = new Stage(StageStyle.TRANSPARENT);
    private final Stage owner = new Stage(StageStyle.UTILITY);
    private final StackPane content = new StackPane();
    private final InstantSource clock;
    private Instant closedAt = Instant.EPOCH;
    private Point2D iconPosition = Point2D.ZERO;

    DashboardPopup(SystemTheme theme, AppIcon icon, InstantSource clock) {
        this.clock = clock;
        content.getStyleClass().add("popup");
        var scene = new Scene(content, Color.TRANSPARENT);
        theme.style(scene);
        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                close();
            }
        });
        stage.setScene(scene);
        stage.setTitle("Car charge planner");
        // where a taskbar still lists the popup (Plasma does), it shows the app's icon
        stage.getIcons().setAll(icon.fxImages());
        // JavaFX cannot keep a window out of the taskbar, but the Windows taskbar leaves out windows that a utility
        // window owns; the owner is shown, invisible and one pixel large, beneath the popup
        owner.setOpacity(0);
        owner.setWidth(1);
        owner.setHeight(1);
        stage.initOwner(owner);
        stage.setAlwaysOnTop(true);
        // the owner can take the focus as it appears; it hands it back instead of the popup closing
        owner.focusedProperty().subscribe(focused -> {
            if (focused && stage.isShowing()) {
                stage.requestFocus();
            }
        });
        // decided once the focus has settled, so a move to the owner does not count as a click elsewhere
        stage.focusedProperty().subscribe(focused -> {
            if (!focused) {
                Platform.runLater(this::closeUnlessFocused);
            }
        });
    }

    /**
     * Opens the popup beside the tray icon; a click on the icon while it is open closes it.
     *
     * @param reported where the tray says the icon is; another process (the panel) says so, so it is used only when
     *     it lies on a screen
     */
    void toggle(DashboardView view, Point2D reported) {
        if (stage.isShowing()) {
            close();
            return;
        }
        if (Duration.between(closedAt, clock.instant()).compareTo(SAME_CLICK) < 0) {
            return;
        }
        var screens = Screen.getScreens().stream().map(Screen::getBounds).toList();
        iconPosition = PopupPlacement.onAScreen(reported, screens, Screen.getPrimary().getVisualBounds());
        view.setDetachable(true);
        content.getChildren().setAll(view);
        owner.setX(iconPosition.getX());
        owner.setY(iconPosition.getY());
        owner.show();
        // its size is only known once it shows: until it has its place beside the icon, it is see-through
        stage.setOpacity(0);
        stage.show();
        fitToScreen();
        stage.setOpacity(1);
        stage.toFront();
        stage.requestFocus();
    }

    boolean isShowing() {
        return stage.isShowing();
    }

    private void closeUnlessFocused() {
        if (!stage.isFocused() && !owner.isFocused()) {
            close();
        }
    }

    void close() {
        if (stage.isShowing()) {
            stage.hide();
            owner.hide();
            closedAt = clock.instant();
        }
    }

    /** Fits the popup to new content, still beside the icon. */
    void fit() {
        if (stage.isShowing()) {
            fitToScreen();
        }
    }

    /**
     * Sizes the popup to its content, at most as tall as the screen's free area (the day scrolls inside), and places
     * it beside the icon. Only while it shows: a hidden window's styles are not resolved, so it would measure wrong.
     */
    private void fitToScreen() {
        var area = screenOf(iconPosition).getVisualBounds();
        stage.sizeToScene();
        stage.setHeight(Math.min(stage.getHeight(), area.getHeight() - 2 * PopupPlacement.MARGIN));
        var size = new Dimension2D(stage.getWidth(), stage.getHeight());
        var position = PopupPlacement.position(iconPosition, size, area);
        stage.setX(position.getX());
        stage.setY(position.getY());
    }

    private static Screen screenOf(Point2D point) {
        return Screen.getScreensForRectangle(point.getX(), point.getY(), 1, 1).stream()
                .findFirst()
                .orElse(Screen.getPrimary());
    }
}
