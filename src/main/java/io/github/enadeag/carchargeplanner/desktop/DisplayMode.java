package io.github.enadeag.carchargeplanner.desktop;

import java.util.Arrays;
import java.util.Optional;

/** How the app shows itself: as a tray icon with the dashboard as its popup, or as a window. */
public enum DisplayMode {
    /** A tray icon when the desktop has a tray, otherwise a window. */
    AUTOMATIC("automatic"),
    /** A tray icon; the app waits a while for the tray at every start, and shows a window only when none comes. */
    TRAY("tray"),
    /** A window, also when the desktop has a tray; closing it quits the app. */
    WINDOW("window");

    private final String code;

    DisplayMode(String code) {
        this.code = code;
    }

    /**
     * The name the app settings file writes.
     *
     * @return such as "window"
     */
    public String code() {
        return code;
    }

    /**
     * The mode with this name in the app settings file.
     *
     * @param code such as "tray", in any case; may be empty or unknown
     * @return the mode, or empty when there is none with the name
     */
    public static Optional<DisplayMode> ofCode(String code) {
        return Arrays.stream(values()).filter(mode -> mode.code.equalsIgnoreCase(code)).findFirst();
    }
}
