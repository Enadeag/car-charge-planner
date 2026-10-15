package io.github.enadeag.carchargeplanner.desktop;

import java.util.Optional;

/**
 * The desktop app's own settings, kept on this computer and not by the engine (see "App settings" in CONTEXT.md).
 *
 * @param language the language the owner chose for the app's texts; empty to follow the system's language
 * @param units the units distances and consumption are shown in
 * @param mode how the app shows itself
 * @param appearance whether the app's windows are light, dark or as the system is
 */
public record AppSettings(Optional<Language> language, Units units, DisplayMode mode, Appearance appearance) {

    /**
     * The settings of an app that was never set otherwise: the system's language and appearance, the automatic mode,
     * and the units of the owner's country.
     *
     * @param units the units of the owner's country, as {@link Units#ofCountry(String)} finds them
     * @return the settings that follow the system
     */
    public static AppSettings following(Units units) {
        return new AppSettings(Optional.empty(), units, DisplayMode.AUTOMATIC, Appearance.SYSTEM);
    }
}
