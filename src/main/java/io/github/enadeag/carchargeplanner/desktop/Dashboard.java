package io.github.enadeag.carchargeplanner.desktop;

import io.github.enadeag.carchargeplanner.engine.Outlook;
import javafx.application.ColorScheme;
import javafx.application.Platform;
import javafx.beans.value.ObservableValue;

/**
 * The one screen that shows the plan, for now in a window of its own.
 *
 * <p>Make it on the JavaFX thread; its public methods may be called from any thread.
 */
public final class Dashboard {

    private final OutlookMapper mapper;
    private final DashboardView view;
    private final DashboardWindow window;

    /**
     * @param colorScheme the system's light or dark ({@code Platform.getPreferences().colorSchemeProperty()})
     * @param forecastSource the forecast provider's name, for the footer
     */
    public Dashboard(AppIcon icon, ObservableValue<ColorScheme> colorScheme, String forecastSource) {
        this.mapper = new OutlookMapper(forecastSource);
        this.view = new DashboardView(() -> { });
        this.window = new DashboardWindow(new SystemTheme(colorScheme), icon);
        view.show(mapper.loading());
    }

    public void open() {
        Platform.runLater(() -> window.show(view));
    }

    public void show(Outlook outlook) {
        var model = mapper.map(outlook);
        Platform.runLater(() -> {
            view.show(model);
            window.fit();
        });
    }

    /** Without a tray, closing the window is the only way to quit. */
    public void quitWhenClosed(Runnable quit) {
        Platform.runLater(() -> window.onClose(quit));
    }
}
