package nl.enadeag.carchargeplanner.desktop.system;

/**
 * What a notification says, and what a click on it opens.
 *
 * @param title the first line, in bold on most desktops, such as "Charge window 10:00 – 15:00"; not empty
 * @param body the sentences below it; may be empty
 * @param opens what a click on it opens
 */
public record NotificationText(String title, String body, NotificationTarget opens) {

    /**
     * A notification whose click opens the dashboard.
     *
     * @param title the first line, in bold on most desktops, such as "Charge window 10:00 – 15:00"; not empty
     * @param body the sentences below it; may be empty
     */
    public NotificationText(String title, String body) {
        this(title, body, NotificationTarget.DASHBOARD);
    }
}
