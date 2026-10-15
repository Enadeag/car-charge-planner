package io.github.enadeag.carchargeplanner.desktop;

import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import javafx.application.Application;
import javafx.application.ColorScheme;
import javafx.application.Platform;
import javafx.scene.Scene;

/** Light or dark, as the system is set, also when the owner switches while the app runs. */
final class SystemTheme {

    private SystemTheme() {
    }

    static void follow(Scene scene) {
        var scheme = Platform.getPreferences().colorSchemeProperty();
        apply(scene, scheme.get());
        scheme.addListener((property, old, now) -> apply(scene, now));
    }

    private static void apply(Scene scene, ColorScheme scheme) {
        boolean dark = scheme == ColorScheme.DARK;
        // AtlantaFX themes are user agent stylesheets, which JavaFX keeps per application, not per scene
        Application.setUserAgentStylesheet(
                dark ? new PrimerDark().getUserAgentStylesheet() : new PrimerLight().getUserAgentStylesheet());
        var classes = scene.getRoot().getStyleClass();
        classes.remove("dark");
        if (dark) {
            classes.add("dark");
        }
    }
}
