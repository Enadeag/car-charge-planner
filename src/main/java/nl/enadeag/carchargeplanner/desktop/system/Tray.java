package nl.enadeag.carchargeplanner.desktop.system;

import java.util.function.Consumer;
import javafx.geometry.Point2D;

/** The icon in the system's tray that shows the app is running and the plan's state. Each system has its own. */
public interface Tray {

    /**
     * Puts the icon in the tray. The actions are called on a thread of the tray's, not the JavaFX thread; an action
     * may remove the tray.
     *
     * @param actions what a click on the icon and the entries of its menu do
     * @throws TrayUnavailable when the system has no tray this app can use; the app then shows a window instead
     */
    void show(Actions actions) throws TrayUnavailable;

    /**
     * Shows the plan's state on the icon and in its tooltip. May be called from any thread, also before
     * {@link #show(Actions)}: the icon then appears with it.
     *
     * @param status the dot and the sentence to show until the next call; never null
     */
    void showStatus(TrayStatus status);

    /**
     * Gives the entries of the icon's menu new labels, such as in another language. May be called from any thread,
     * also before {@link #show(Actions)}: the menu then appears with them.
     *
     * @param labels what the entries say until the next call; never null
     */
    void showLabels(MenuLabels labels);

    /** Takes the icon out of the tray; does nothing when it is not there. */
    void remove();

    /**
     * What the tray icon and its menu do.
     *
     * @param clicked the action run on a click on the icon; it is given where the icon is on the screen, in JavaFX's
     *     coordinates
     * @param open the action for the menu's "Open dashboard"
     * @param settings the action for the menu's "Settings…"
     * @param about the action for the menu's "About…"
     * @param quit the action for the menu's "Quit"
     */
    record Actions(Consumer<Point2D> clicked, Runnable open, Runnable settings, Runnable about, Runnable quit) {
    }

    /**
     * What the entries of the icon's menu say, in the owner's language.
     *
     * @param open the entry that opens the dashboard, such as "Open dashboard"; not empty
     * @param settings the entry that opens Settings, such as "Settings…"; not empty
     * @param about the entry that opens Settings on About, such as "About…"; not empty
     * @param quit the entry that quits the app, such as "Quit"; not empty
     */
    record MenuLabels(String open, String settings, String about, String quit) {
    }

    /** The system has no tray this app can use, such as GNOME without the AppIndicator extension. */
    class TrayUnavailable extends Exception {

        /**
         * Makes the exception.
         *
         * @param message what is missing, for the log, such as "The desktop has no tray that this app can use"
         * @param cause the error the system gave, for the log; may be null when there was none
         */
        public TrayUnavailable(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
