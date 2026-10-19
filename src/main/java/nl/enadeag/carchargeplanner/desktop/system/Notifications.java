package nl.enadeag.carchargeplanner.desktop.system;

/** The system's notifications: shows the app's own and reports a click on one. Each system has its own. */
public interface Notifications {

    /**
     * Sets what a click on one of the app's notifications does. Call it before the first {@link #show}; clicks on
     * other apps' notifications are never passed on.
     *
     * @param clicked the action for a click on a notification or its button; called on a thread of the notifications',
     *     not the JavaFX thread
     */
    void onClick(Runnable clicked);

    /**
     * Shows a notification. Never throws: when the system cannot show it, that is logged once and the app goes on.
     *
     * @param text what the notification says
     */
    void show(NotificationText text);

    /** Stops listening for clicks and lets go of what the notifications held; does nothing a second time. */
    void close();
}
