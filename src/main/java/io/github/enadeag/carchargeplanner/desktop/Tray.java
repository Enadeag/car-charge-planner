package io.github.enadeag.carchargeplanner.desktop;

/** The icon in the system's tray that shows the app is running. Each system has its own. */
public interface Tray {

    /**
     * Puts the icon in the tray. A click on it calls {@code actions.open()}; its menu offers open and quit.
     * The actions are called on a thread of the tray's, not the JavaFX thread; an action may remove the tray.
     */
    void show(TrayActions actions) throws TrayUnavailable;

    void remove();

    record TrayActions(Runnable open, Runnable quit) {
    }

    class TrayUnavailable extends Exception {

        public TrayUnavailable(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
