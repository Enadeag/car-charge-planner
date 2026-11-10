package nl.enadeag.carchargeplanner.platform.windows;

import nl.enadeag.carchargeplanner.desktop.system.NotificationTarget;
import nl.enadeag.carchargeplanner.desktop.system.NotificationText;
import java.lang.System.Logger.Level;
import java.util.function.Consumer;

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
    private Consumer<NotificationTarget> clicked = _ -> { };
    /** What the latest message opens while it waits for a click; null when none waits. */
    private NotificationTarget waiting;
    private boolean warned;

    TrayMessages() {
    }

    /** The action is given what the message opens, and runs on the thread that reports the click. */
    synchronized void onClick(Consumer<NotificationTarget> clicked) {
        this.clicked = clicked;
    }

    /** The icon is in the tray: messages can be shown from now on. */
    synchronized void attach(Display display) {
        this.display = display;
    }

    /** Stops passing clicks on: a click on a message that is still shown does nothing from now on. */
    synchronized void stopPassingClicks() {
        clicked = _ -> { };
    }

    /** The icon left the tray: messages are no longer shown. */
    synchronized void detach() {
        display = null;
        waiting = null;
    }

    /**
     * Shows a message, or logs once that there is no tray icon to show it on.
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
        waiting = text.opens();
    }

    /**
     * Windows reported a click on the icon's message: runs the action, with what the latest message opens, when a
     * message was waiting for one.
     *
     * @return true when the click was a message's, false when none was waiting, such as for a double click
     */
    boolean claimClick() {
        Consumer<NotificationTarget> action;
        NotificationTarget target;
        synchronized (this) {
            if (waiting == null) {
                return false;
            }
            target = waiting;
            waiting = null;
            action = clicked;
        }
        action.accept(target);
        return true;
    }
}
