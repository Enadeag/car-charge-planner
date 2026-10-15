package io.github.enadeag.carchargeplanner.desktop;

/**
 * What the tray icon tells about the plan.
 *
 * @param dot the dot on the icon
 * @param text the same in words, for the tooltip and screen readers, such as "Charge on sun now, until 14:30, at
 *     10 A."
 */
public record TrayStatus(StatusDot dot, String text) {
}
