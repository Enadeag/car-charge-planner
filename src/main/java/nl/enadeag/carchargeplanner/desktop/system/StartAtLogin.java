package nl.enadeag.carchargeplanner.desktop.system;

import java.io.IOException;

/**
 * Start at login: the app launching itself, hidden in the tray, when the user signs in. Each system keeps the choice
 * in its own place, which is read every time, so a change made outside the app is seen too.
 */
public interface StartAtLogin {

    /** The argument the app is started with at login, so that it starts hidden in the tray. */
    String BACKGROUND = "--background";

    /**
     * Whether the app starts at login.
     *
     * @return true when the system has an entry of this app's that starts it at login
     * @throws IOException when the system's entries cannot be read
     */
    boolean isOn() throws IOException;

    /**
     * Makes the app start at login, hidden; an entry that was there already is replaced.
     *
     * @throws IOException when the system's entry cannot be written
     */
    void switchOn() throws IOException;

    /**
     * Stops the app from starting at login; does nothing when it does not.
     *
     * @throws IOException when the system's entry cannot be removed
     */
    void switchOff() throws IOException;
}
