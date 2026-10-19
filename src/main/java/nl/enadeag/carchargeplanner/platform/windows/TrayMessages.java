package nl.enadeag.carchargeplanner.platform.windows;

import nl.enadeag.carchargeplanner.desktop.system.NotificationText;
import java.lang.System.Logger.Level;

/**
 * Notifications as messages of the tray icon, which Windows shows as toasts. A click names no message, so it counts
 * for the latest one only, and only once: a click that comes in when no message is waiting, such as a double click
 * on the icon, is not passed on.
 */
final class TrayMessages {

    private static final System.Logger LOG = System.getLogger(TrayMessages.class.getName());

    /** Shows a message on the tray icon; AWT's {@code TrayIcon.displayMessage} in the app. */
    @FunctionalInterface
    interface Display {

        /**
         * Shows the message.
         *
         * @param title the first line
         * @param body the text below it; may be empty
         */
        void show(String title, String body);
    }

    private Display display;
    private Runnable clicked = () -> { };
    private boolean waiting;
    private boolean warned;

    /** Makes the messages; none can be shown until {@link #attach(Display)}. */
    TrayMessages() {
    }

    /**
     * Sets what a click on a message does.
     *
     * @param clicked the action, run on the thread that reports the click
     */
    synchronized void onClick(Runnable clicked) {
        this.clicked = clicked;
    }

    /**
     * The icon is in the tray: messages can be shown from now on.
     *
     * @param display shows a message on the icon
     */
    synchronized void attach(Display display) {
        this.display = display;
    }

    /** Stops passing clicks on: a click on a message that is still shown does nothing from now on. */
    synchronized void stopPassingClicks() {
        clicked = () -> { };
    }

    /** The icon left the tray: messages are no longer shown. */
    synchronized void detach() {
        display = null;
        waiting = false;
    }

    /**
     * Shows a message, or logs once that there is no tray icon to show it on.
     *
     * @param text what the message says
     */
    synchronized void show(NotificationText text) {
        if (display == null) {
            if (!warned) {
                warned = true;
                LOG.log(Level.WARNING, "Could not show a notification: there is no tray icon");
            }
            return;
        }
        display.show(text.title(), text.body());
        waiting = true;
    }

    /**
     * Windows reported a click on the icon's message: runs the action when a message was waiting for one.
     *
     * @return true when the click was a message's, false when none was waiting, such as for a double click
     */
    boolean claimClick() {
        Runnable action;
        synchronized (this) {
            if (!waiting) {
                return false;
            }
            waiting = false;
            action = clicked;
        }
        action.run();
        return true;
    }
}
