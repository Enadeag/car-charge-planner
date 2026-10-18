package io.github.enadeag.carchargeplanner.desktop.system;

/**
 * What the tray icon tells about the plan.
 *
 * @param dot the dot on the icon
 * @param text the same in one or two English sentences, such as "Charge on sun now, until 14:30, at 10 A."; may be
 *     empty
 */
public record TrayStatus(StatusDot dot, String text) {
}
