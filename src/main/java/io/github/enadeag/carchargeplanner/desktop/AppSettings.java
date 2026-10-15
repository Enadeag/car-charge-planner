package io.github.enadeag.carchargeplanner.desktop;

import java.util.Optional;

/**
 * The desktop app's own settings, kept on this computer and not by the engine (see "App settings" in CONTEXT.md).
 *
 * @param language the language the owner chose for the app's texts; empty to follow the system's language
 */
public record AppSettings(Optional<Language> language) {

    /** The settings of an app that was never set otherwise: everything follows the system. */
    public static final AppSettings SYSTEM = new AppSettings(Optional.empty());
}
