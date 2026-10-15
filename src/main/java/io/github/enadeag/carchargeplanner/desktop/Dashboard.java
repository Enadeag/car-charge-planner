package io.github.enadeag.carchargeplanner.desktop;

import io.github.enadeag.carchargeplanner.engine.Outlook;
import io.github.enadeag.carchargeplanner.engine.config.Percent;
import java.time.InstantSource;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import javafx.application.ColorScheme;
import javafx.application.Platform;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Point2D;

/**
 * The one screen that shows the plan: a popup beside the tray icon, or a window. Both show the same
 * {@link DashboardView}; it moves to the window when the popup is detached.
 *
 * <p>When the owner chooses another language, the dashboard is made again in it and shows the same outlook, on the
 * same day, in the popup or window that shows it now.
 *
 * <p>Make it on the JavaFX thread; its public methods may be called from any thread.
 */
public final class Dashboard {

    private final ObservableValue<Boolean> reducedMotion;
    private final DashboardPopup popup;
    private final DashboardWindow window;
    // the fields below belong to the JavaFX thread
    private Messages messages;
    private DashboardView view;
    private Optional<Outlook> outlook = Optional.empty();
    private Optional<IntConsumer> batteryEntered = Optional.empty();
    private Optional<Runnable> away = Optional.empty();
    private Optional<Runnable> settings = Optional.empty();

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
     * @param language the language the texts are in, followed while the app runs; never null
     */
    public Dashboard(AppIcon icon, ObservableValue<ColorScheme> colorScheme, ObservableValue<Boolean> reducedMotion,
            InstantSource clock, TaskbarHiding taskbarHiding, LanguageChoice language) {
        var theme = new SystemTheme(colorScheme);
        this.reducedMotion = reducedMotion;
        this.popup = new DashboardPopup(theme, icon, clock, taskbarHiding);
        this.window = new DashboardWindow(theme, icon);
        this.messages = language.messages();
        this.view = newView();
        language.onChange(chosen -> Platform.runLater(() -> speak(chosen)));
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
        Platform.runLater(() -> {
            this.outlook = Optional.of(outlook);
            view.show(new OutlookMapper(messages).map(outlook));
        });
    }

    /**
     * Lets the owner enter the battery level the car shows, on today's battery card.
     *
     * @param entered gets the level the owner saved; called on the JavaFX thread, so it should hand the work on
     */
    public void onBatteryEntered(Consumer<Percent> entered) {
        Platform.runLater(() -> {
            batteryEntered = Optional.of(pct -> entered.accept(new Percent(pct)));
            view.onBatteryEntered(batteryEntered.get());
        });
    }

    /**
     * Lets the footer's Away button open the away periods.
     *
     * @param action what the button does; called on the JavaFX thread
     */
    public void onAway(Runnable action) {
        Platform.runLater(() -> {
            away = Optional.of(action);
            view.onAway(action);
        });
    }

    /**
     * Lets the footer's Settings button open Settings.
     *
     * @param action what the button does; called on the JavaFX thread
     */
    public void onSettings(Runnable action) {
        Platform.runLater(() -> {
            settings = Optional.of(action);
            view.onSettings(action);
        });
    }

    /**
     * Makes closing the window quit the app instead of hiding it: without a tray it is the only way to quit.
     *
     * @param quit what closing the window does; called on the JavaFX thread
     */
    public void quitWhenClosed(Runnable quit) {
        Platform.runLater(() -> window.onClose(quit));
    }

    /**
     * The same outlook in another language: a new view, made as the first one was, takes the place of the one shown,
     * on the day that was selected.
     */
    private void speak(Messages chosen) {
        messages = chosen;
        var before = view;
        view = newView();
        view.selectDay(before.selectedDay());
        popup.replace(before, view);
        window.replace(before, view);
    }

    /** A view in the language spoken now, with the actions given so far, showing the latest outlook. */
    private DashboardView newView() {
        var made = new DashboardView(this::detach, this::fitToContent, reducedMotion, messages);
        batteryEntered.ifPresent(made::onBatteryEntered);
        away.ifPresent(made::onAway);
        settings.ifPresent(made::onSettings);
        var mapper = new OutlookMapper(messages);
        made.show(outlook.map(mapper::map).orElseGet(mapper::loading));
        return made;
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
