package io.github.enadeag.carchargeplanner.desktop;

import java.util.function.Consumer;
import javafx.geometry.Point2D;

/** The icon in the system's tray that shows the app is running. Each system has its own. */
public interface Tray {

    /**
     * Puts the icon in the tray. The actions are called on a thread of the tray's, not the JavaFX thread; an action
     * may remove the tray.
     */
    void show(TrayActions actions) throws TrayUnavailable;

    void remove();

    /**
     * @param clicked a click on the icon, with where the icon is on the screen, in JavaFX's coordinates
     * @param open the menu's "Open dashboard"
     * @param quit the menu's "Quit"
     */
    record TrayActions(Consumer<Point2D> clicked, Runnable open, Runnable quit) {
    }

    class TrayUnavailable extends Exception {

        public TrayUnavailable(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
