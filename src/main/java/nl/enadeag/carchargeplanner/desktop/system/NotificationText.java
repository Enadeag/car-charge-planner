package nl.enadeag.carchargeplanner.desktop.system;

/**
 * What a notification says.
 *
 * @param title the first line, in bold on most desktops, such as "Charge window 10:00 – 15:00"; not empty
 * @param body the sentences below it; may be empty
 */
public record NotificationText(String title, String body) {
}
