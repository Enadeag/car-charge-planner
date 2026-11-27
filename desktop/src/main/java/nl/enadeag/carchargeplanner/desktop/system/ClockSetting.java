package nl.enadeag.carchargeplanner.desktop.system;

import java.util.function.Consumer;

/** The system's setting for a 12- or 24-hour clock, which every time the app shows or reads follows. */
@FunctionalInterface
public interface ClockSetting {

    /**
     * Follows the setting while the app runs: gives the clock now, on the calling thread before this returns, and
     * then again whenever it changes, on a thread of the setting's own. A setting that cannot be read gives
     * {@link ClockFormat#LANGUAGE}.
     *
     * @param changed gets each clock; it should hand it on to the thread that uses it
     */
    void follow(Consumer<ClockFormat> changed);
}
