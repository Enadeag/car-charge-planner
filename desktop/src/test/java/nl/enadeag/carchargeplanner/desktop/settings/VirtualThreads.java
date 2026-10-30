package nl.enadeag.carchargeplanner.desktop.settings;

import nl.enadeag.carchargeplanner.desktop.app.Background;
import javafx.application.Platform;

/** The slow work of a window on a virtual thread each, as the app does it, and the outcome on the JavaFX thread. */
public final class VirtualThreads {

    private VirtualThreads() {
    }

    /**
     * Makes a helper that works as the app's does.
     *
     * @return a new helper
     */
    public static Background background() {
        return new Background(task -> Thread.ofVirtual().name("background").start(task), Platform::runLater);
    }
}
