package io.github.enadeag.carchargeplanner.desktop;

import io.github.enadeag.carchargeplanner.engine.Outlook;
import io.github.enadeag.carchargeplanner.engine.config.Percent;
import java.time.InstantSource;
import java.util.function.Consumer;
import javafx.application.ColorScheme;
import javafx.application.Platform;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Point2D;

/**
 * The one screen that shows the plan: a popup beside the tray icon, or a window. Both show the same
 * {@link DashboardView}; it moves to the window when the popup is detached.
 *
 * <p>Make it on the JavaFX thread; its public methods may be called from any thread.
 */
public final class Dashboard {

    private final OutlookMapper mapper;
    private final DashboardView view;
    private final DashboardPopup popup;
    private final DashboardWindow window;

    /**
     * Makes the dashboard, hidden, showing that the forecast is being fetched until {@link #show(Outlook)} is called.
     *
     * @param icon the app icon, for the window's title bar and the taskbar
     * @param colorScheme the system's light or dark, followed while the app runs
     *     ({@code Platform.getPreferences().colorSchemeProperty()}); never null
     * @param reducedMotion true while the system asks for reduced motion
     *     ({@code Platform.getPreferences().reducedMotionProperty()}); the charts then change without moving; never
     *     null
     * @param clock tells when the popup closed, so the click on the tray icon that closed it does not open it again
     * @param taskbarHiding how this system keeps the popup out of its taskbar
     * @param messages the texts, in the owner's language
     */
    public Dashboard(AppIcon icon, ObservableValue<ColorScheme> colorScheme, ObservableValue<Boolean> reducedMotion,
            InstantSource clock, TaskbarHiding taskbarHiding, Messages messages) {
        var theme = new SystemTheme(colorScheme);
        this.mapper = new OutlookMapper(messages);
        this.view = new DashboardView(this::detach, this::fitToContent, reducedMotion, messages);
        this.popup = new DashboardPopup(theme, icon, clock, taskbarHiding);
        this.window = new DashboardWindow(theme, icon);
        view.show(mapper.loading());
    }

    /**
     * A click on the tray icon: opens the popup beside it, or closes it when it is open. While the dashboard is shown
     * as a window, brings the window to the front instead.
     *
     * @param icon where the tray says its icon is, in JavaFX's screen coordinates; reported by another process, so
     *     a position off every screen is replaced by the primary screen's bottom right
     */
    public void popUp(Point2D icon) {
        Platform.runLater(() -> {
            if (window.isShowing()) {
                window.show(view);
            } else {
                popup.toggle(view, icon);
            }
        });
    }

    /** Shows the dashboard as a window, taking it out of the popup if it is open there. */
    public void openWindow() {
        Platform.runLater(this::detach);
    }

    /**
     * Shows a new outlook from the engine, keeping the selected day.
     *
     * @param outlook the engine's latest plan, or why there is none; never null
     */
    public void show(Outlook outlook) {
        var model = mapper.map(outlook);
        Platform.runLater(() -> view.show(model));
    }

    /**
     * Lets the owner enter the battery level the car shows, on today's battery card.
     *
     * @param entered gets the level the owner saved; called on the JavaFX thread, so it should hand the work on
     */
    public void onBatteryEntered(Consumer<Percent> entered) {
        Platform.runLater(() -> view.onBatteryEntered(pct -> entered.accept(new Percent(pct))));
    }

    /**
     * Lets the footer's Settings button open Settings.
     *
     * @param action what the button does; called on the JavaFX thread
     */
    public void onSettings(Runnable action) {
        Platform.runLater(() -> view.onSettings(action));
    }

    /**
     * Makes closing the window quit the app instead of hiding it: without a tray it is the only way to quit.
     *
     * @param quit what closing the window does; called on the JavaFX thread
     */
    public void quitWhenClosed(Runnable quit) {
        Platform.runLater(() -> window.onClose(quit));
    }

    /** Another day or a new plan: the popup or window takes the height it needs. */
    private void fitToContent() {
        popup.fit();
        window.fit();
    }

    private void detach() {
        popup.close();
        window.show(view);
    }
}
