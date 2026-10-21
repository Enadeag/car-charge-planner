package nl.enadeag.carchargeplanner.desktop.system;

import java.util.function.Consumer;

/** The system's setting for larger text, which the windows follow on top of the display's scaling. */
@FunctionalInterface
public interface TextSizeSetting {

    /**
     * Follows the setting while the app runs: gives the size now, on the calling thread before this returns, and then
     * again whenever it changes, on a thread of the setting's own. A setting that cannot be read gives
     * {@link TextSize#NORMAL}.
     *
     * @param changed gets each size; it should hand it to the JavaFX thread
     */
    void follow(Consumer<TextSize> changed);
}
