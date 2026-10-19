package nl.enadeag.carchargeplanner.desktop.dashboard;

import nl.enadeag.carchargeplanner.desktop.system.AppIcon;
import nl.enadeag.carchargeplanner.desktop.system.TaskbarHiding;
import nl.enadeag.carchargeplanner.desktop.text.LanguageChoice;
import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.desktop.theme.SystemTheme;
import nl.enadeag.carchargeplanner.engine.Outlook;
import nl.enadeag.carchargeplanner.engine.away.AwayPeriod;
import nl.enadeag.carchargeplanner.engine.config.Percent;
import java.time.InstantSource;
import java.util.List;
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
    private final DashboardActions actions;
    // the fields below belong to the JavaFX thread
    private Messages messages;
    private DashboardView view;
    /** The latest outlook; null until the engine gives one. */
    private Outlook outlook;
    private List<AwayPeriod> awayPeriods = List.of();

    /**
     * Makes the dashboard, hidden, showing that the forecast is being fetched until {@link #show(Outlook, List)} is
     * called.
     *
     * @param icon the app icon, for the window's title bar and the taskbar
     * @param colorScheme light or dark for the windows, followed while the app runs: the owner's appearance over the
     *     system's ({@link nl.enadeag.carchargeplanner.desktop.app.Appearance#scheme}); never null
     * @param reducedMotion true while the system asks for reduced motion
     *     ({@code Platform.getPreferences().reducedMotionProperty()}); the charts then change without moving; never
     *     null
     * @param clock tells when the popup closed, so the click on the tray icon that closed it does not open it again
     * @param taskbarHiding how this system keeps the popup out of its taskbar
     * @param language the language the texts are in, followed while the app runs; never null
     * @param actions what the dashboard's controls do; never null
     */
    public Dashboard(AppIcon icon, ObservableValue<ColorScheme> colorScheme, ObservableValue<Boolean> reducedMotion,
            InstantSource clock, TaskbarHiding taskbarHiding, LanguageChoice language, DashboardActions actions) {
        if (actions == null) {
            throw new IllegalArgumentException("the dashboard's actions must not be null");
        }
        var theme = new SystemTheme(colorScheme);
        this.reducedMotion = reducedMotion;
        this.actions = actions;
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
     * @param awayPeriods the away periods the owner entered for specific days, as the engine stores them, in order of
     *     their start; may be empty, and those that ended before the plan was made are not listed
     */
    public void show(Outlook outlook, List<AwayPeriod> awayPeriods) {
        var periods = List.copyOf(awayPeriods);
        Platform.runLater(() -> {
            this.outlook = outlook;
            this.awayPeriods = periods;
            view.show(new OutlookMapper(messages).map(outlook, periods));
        });
    }

    /**
     * Makes closing the window quit the app, with {@link DashboardActions#quit()}, instead of hiding it: without a
     * tray it is the only way to quit.
     */
    public void quitWhenClosed() {
        Platform.runLater(() -> window.onClose(actions.quit()));
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

    /** A view in the language spoken now, with the dashboard's actions, showing the latest outlook. */
    private DashboardView newView() {
        var made = new DashboardView(this::detach, this::fitToContent, reducedMotion, messages);
        made.onBatteryEntered(pct -> actions.batteryEntered().accept(new Percent(pct)));
        made.onAwayChange(actions.awayAdded(), actions.awayRemoved());
        made.onAway(actions.away());
        made.onSettings(actions.settings());
        made.onSetUp(actions.setUp());
        var mapper = new OutlookMapper(messages);
        made.show(outlook == null ? mapper.loading() : mapper.map(outlook, awayPeriods));
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
