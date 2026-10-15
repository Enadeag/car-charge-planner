package io.github.enadeag.carchargeplanner.desktop;

import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import java.util.ArrayList;
import java.util.List;
import javafx.application.Application;
import javafx.application.ColorScheme;
import javafx.beans.value.ObservableValue;
import javafx.scene.Scene;

/** Light or dark for the dashboard's scenes, as the colour scheme is set, also when it changes while the app runs. */
final class SystemTheme {

    private static final String STYLESHEET = SystemTheme.class.getResource("dashboard.css").toExternalForm();

    private final ObservableValue<ColorScheme> scheme;
    private final List<Scene> scenes = new ArrayList<>();

    /** @param scheme the system's colour scheme ({@code Platform.getPreferences().colorSchemeProperty()}) */
    SystemTheme(ObservableValue<ColorScheme> scheme) {
        this.scheme = scheme;
        scheme.subscribe(now -> scenes.forEach(scene -> apply(scene, now)));
    }

    void style(Scene scene) {
        scene.getStylesheets().add(STYLESHEET);
        scenes.add(scene);
        apply(scene, scheme.getValue());
    }

    private static void apply(Scene scene, ColorScheme scheme) {
        boolean dark = scheme == ColorScheme.DARK;
        // AtlantaFX's theme also has to reach tooltips, which are windows of their own: set it for the application
        Application.setUserAgentStylesheet(
                dark ? new PrimerDark().getUserAgentStylesheet() : new PrimerLight().getUserAgentStylesheet());
        var classes = scene.getRoot().getStyleClass();
        classes.remove("dark");
        if (dark) {
            classes.add("dark");
        }
    }
}
