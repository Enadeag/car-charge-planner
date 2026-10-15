package io.github.enadeag.carchargeplanner.desktop;

import io.github.enadeag.carchargeplanner.engine.Outlook;
import java.time.InstantSource;
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
     * @param colorScheme the system's light or dark ({@code Platform.getPreferences().colorSchemeProperty()})
     * @param forecastSource the forecast provider's name, for the footer
     */
    public Dashboard(AppIcon icon, ObservableValue<ColorScheme> colorScheme, String forecastSource, InstantSource clock) {
        var theme = new SystemTheme(colorScheme);
        this.mapper = new OutlookMapper(forecastSource);
        this.view = new DashboardView(this::detach);
        this.popup = new DashboardPopup(theme, clock);
        this.window = new DashboardWindow(theme, icon);
        view.show(mapper.loading());
    }

    /** A click on the tray icon at {@code icon}: opens the popup there, or closes it when it is open. */
    public void popUp(Point2D icon) {
        Platform.runLater(() -> {
            if (window.isShowing()) {
                window.show(view);
            } else {
                popup.toggle(view, icon);
            }
        });
    }

    public void openWindow() {
        Platform.runLater(this::detach);
    }

    public void show(Outlook outlook) {
        var model = mapper.map(outlook);
        Platform.runLater(() -> {
            view.show(model);
            popup.fit();
            window.fit();
        });
    }

    /** Without a tray, closing the window is the only way to quit. */
    public void quitWhenClosed(Runnable quit) {
        Platform.runLater(() -> window.onClose(quit));
    }

    private void detach() {
        popup.close();
        window.show(view);
    }
}
