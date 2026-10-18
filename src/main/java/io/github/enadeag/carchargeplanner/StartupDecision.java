package io.github.enadeag.carchargeplanner;

import io.github.enadeag.carchargeplanner.desktop.app.DisplayMode;
import java.time.Duration;

/**
 * How the app shows itself as it starts: whether it puts an icon in the tray, how long it waits for a tray, and how
 * the dashboard starts.
 *
 * @param mode how the owner wants the app shown
 * @param background the app was started at login, hidden
 * @param notificationsNeedTheTray the system shows notifications as the tray icon's messages, as Windows does
 */
record StartupDecision(DisplayMode mode, boolean background, boolean notificationsNeedTheTray) {

    /** At login the panel may come up a moment after the app. */
    static final Duration TRAY_WAIT = Duration.ofSeconds(5);

    /**
     * Checks that the mode is given.
     *
     * @throws IllegalArgumentException when the mode is null
     */
    StartupDecision {
        if (mode == null) {
            throw new IllegalArgumentException("the display mode must not be null");
        }
    }

    /**
     * Whether the app puts its icon in the tray: unless the owner wants a window, and then still when the
     * notifications need it.
     *
     * @return true to try the tray
     */
    boolean triesTheTray() {
        return mode != DisplayMode.WINDOW || notificationsNeedTheTray;
    }

    /**
     * How long to keep trying the tray: a while at login or when the owner asked for a tray, else one try.
     *
     * @return {@link #TRAY_WAIT}, or zero
     */
    Duration trayWait() {
        return background || mode == DisplayMode.TRAY ? TRAY_WAIT : Duration.ZERO;
    }

    /**
     * How the dashboard starts.
     *
     * @param trayShown whether the tray icon is there
     * @return the popup behind the tray icon, or the window, opened unless the app started at login
     */
    DashboardStart dashboard(boolean trayShown) {
        if (trayShown && mode != DisplayMode.WINDOW) {
            return DashboardStart.POPUP;
        }
        return background ? DashboardStart.HIDDEN_WINDOW : DashboardStart.WINDOW;
    }
}
