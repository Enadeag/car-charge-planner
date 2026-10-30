package nl.enadeag.carchargeplanner.desktop.dashboard;

import nl.enadeag.carchargeplanner.desktop.system.AppIcon;
import nl.enadeag.carchargeplanner.desktop.system.TaskbarHiding;
import nl.enadeag.carchargeplanner.desktop.theme.WindowTheme;
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

/**
 * The dashboard as a popup next to the tray icon. A click elsewhere or Escape closes it; Escape in an open entry, such
 * as the battery level's, closes only the entry.
 */
final class DashboardPopup {

    /**
     * A click on the tray icon while the popup is open first takes the focus away, which closes it, and only then
     * reaches us as a request to open: within this time after closing, that request is the same click.
     */
    private static final Duration SAME_CLICK = Duration.ofMillis(400);

    private final Stage stage = new Stage(StageStyle.TRANSPARENT);
    private final Stage owner = new Stage(StageStyle.UTILITY);
    private final boolean owned;
    private final StackPane content = new StackPane();
    private final InstantSource clock;
    private Instant closedAt = Instant.EPOCH;
    private Point2D iconPosition = Point2D.ZERO;

    /**
     * Makes the popup, hidden.
     *
     * @param theme makes the popup light or dark as the system is
     * @param icon the app icon, for a taskbar that lists the popup
     * @param appName the app's name, for a taskbar that lists the popup
     * @param clock tells when the popup closed, so the click on the tray icon that closed it does not open it again
     * @param taskbarHiding how this system keeps the popup out of its taskbar
     */
    DashboardPopup(WindowTheme theme, AppIcon icon, String appName, InstantSource clock,
            TaskbarHiding taskbarHiding) {
        this.clock = clock;
        this.owned = taskbarHiding == TaskbarHiding.OWNER_WINDOW;
        content.getStyleClass().add("popup");
        var scene = new Scene(content, Color.TRANSPARENT);
        theme.style(scene);
        // a handler, not a filter: Escape in the battery or away entry closes only the entry, which consumes it
        scene.addEventHandler(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                close();
                event.consume();
            }
        });
        stage.setScene(scene);
        stage.setTitle(appName);
        // where a taskbar still lists the popup (Plasma does), it shows the app's icon
        stage.getIcons().setAll(icon.fxImages());
        if (owned) {
            // shown, invisible and one pixel large, beneath the popup
            owner.setOpacity(0);
            owner.setWidth(1);
            owner.setHeight(1);
            stage.initOwner(owner);
        }
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
     * Opens the popup beside the tray icon; a click on the icon while it is open closes it. A request to open that
     * comes within {@code SAME_CLICK} (400 ms) of the popup closing is the click that closed it, and does nothing.
     *
     * @param view the dashboard to show; taken out of the window if it is there
     * @param reported where the tray says the icon is, in JavaFX's screen coordinates; another process (the panel)
     *     says so, so it is used only when it lies on a screen
     */
    void toggle(DashboardView view, Point2D reported) {
        if (stage.isShowing()) {
            close();
            return;
        }
        if (Duration.between(closedAt, clock.instant()).compareTo(SAME_CLICK) >= 0) {
            open(view, reported);
        }
    }

    /** Shows the view beside the icon, in front and with the focus. */
    private void open(DashboardView view, Point2D reported) {
        var screens = Screen.getScreens().stream().map(Screen::getBounds).toList();
        iconPosition = PopupPlacement.onScreenOrFallback(reported, screens, Screen.getPrimary().getVisualBounds());
        view.setDetachable(true);
        content.getChildren().setAll(view);
        if (owned) {
            owner.setX(iconPosition.getX());
            owner.setY(iconPosition.getY());
            owner.show();
        }
        // its size is only known once it shows: until it has its place beside the icon, it is see-through
        stage.setOpacity(0);
        stage.show();
        fitToScreen();
        stage.setOpacity(1);
        stage.toFront();
        stage.requestFocus();
    }

    /**
     * Puts another view where one is shown, such as the same dashboard in another language. The popup stays open or
     * closed, and fits the new view.
     *
     * @param before the view that may be in the popup now
     * @param after the view to show in its place; nothing changes when the popup does not hold {@code before}
     */
    void replace(DashboardView before, DashboardView after) {
        if (content.getChildren().contains(before)) {
            after.setDetachable(true);
            content.getChildren().setAll(after);
            fit();
        }
    }

    private void closeUnlessFocused() {
        if (!stage.isFocused() && !owner.isFocused()) {
            close();
        }
    }

    /** Closes the popup; does nothing when it is closed. */
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
     *
     * <p>Measured rather than {@code Stage.sizeToScene()}: that resizes the window at once, where it is now, so a
     * taller day would reach past the screen's edge until the next frame moves it.
     */
    private void fitToScreen() {
        var area = screenOf(iconPosition).getVisualBounds();
        content.applyCss();
        double width = content.prefWidth(-1);
        double height = Math.min(content.prefHeight(width), area.getHeight() - 2 * PopupPlacement.MARGIN);
        stage.setWidth(width);
        stage.setHeight(height);
        var size = new Dimension2D(width, height);
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
