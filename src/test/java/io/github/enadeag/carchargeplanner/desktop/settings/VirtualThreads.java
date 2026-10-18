package io.github.enadeag.carchargeplanner.desktop.settings;

import io.github.enadeag.carchargeplanner.desktop.app.Background;
import javafx.application.Platform;

/** The slow work of a window on a virtual thread each, as the app does it, and the outcome on the JavaFX thread. */
final class VirtualThreads {

    private VirtualThreads() {
    }

    /**
     * Makes a helper that works as the app's does.
     *
     * @return a new helper
     */
    static Background background() {
        return new Background(task -> Thread.ofVirtual().name("background").start(task), Platform::runLater);
    }
}
