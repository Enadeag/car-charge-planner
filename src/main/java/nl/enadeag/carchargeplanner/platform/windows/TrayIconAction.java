package nl.enadeag.carchargeplanner.platform.windows;

import java.time.Duration;

/**
 * Tells apart what AWT reports alike as the tray icon's action: a double click on the icon, and Enter on the icon
 * after Win+B, the keyboard's way into the notification area. A click on the icon's message is the third; the
 * messages claim that one first. A double click comes right after a press of the mouse on the icon; Enter does not.
 */
final class TrayIconAction {

    private final long doubleClickMillis;
    private long pressedAt = Long.MIN_VALUE;

    /**
     * Makes the decider, with no press seen yet.
     *
     * @param doubleClick how long after a press a second one still makes a double click, as the system is set
     * @throws IllegalArgumentException when the time is negative
     */
    TrayIconAction(Duration doubleClick) {
        if (doubleClick.isNegative()) {
            throw new IllegalArgumentException("the double-click time must not be negative");
        }
        this.doubleClickMillis = doubleClick.toMillis();
    }

    /**
     * The mouse went down on the icon.
     *
     * @param when the press's time in milliseconds, as AWT's events give it
     */
    synchronized void recordPress(long when) {
        pressedAt = when;
    }

    /**
     * Whether an action no message claimed came from the keyboard.
     *
     * @param when the action's time in milliseconds, as AWT's events give it
     * @return true when no press came within the double-click time before it
     */
    synchronized boolean cameFromKeyboard(long when) {
        return pressedAt == Long.MIN_VALUE || when - pressedAt > doubleClickMillis;
    }
}
