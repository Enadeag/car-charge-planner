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
public final class SystemTheme {

    private static final String STYLESHEET = SystemTheme.class.getResource("dashboard.css").toExternalForm();

    private final ObservableValue<ColorScheme> scheme;
    private final List<Scene> scenes = new ArrayList<>();

    /**
     * Makes the theme, following the scheme from now on.
     *
     * @param scheme the colour scheme to follow: the owner's appearance over the system's ({@link Appearance#scheme});
     *     never null
     */
    public SystemTheme(ObservableValue<ColorScheme> scheme) {
        this.scheme = scheme;
        scheme.subscribe(now -> scenes.forEach(scene -> apply(scene, now)));
    }

    /**
     * Gives a scene the dashboard's stylesheet, light or dark as the scheme is, now and whenever it changes, also
     * when the scene gets another root.
     *
     * @param scene the scene to style; the theme keeps it for as long as the app runs
     */
    public void style(Scene scene) {
        scene.getStylesheets().add(STYLESHEET);
        scenes.add(scene);
        scene.rootProperty().subscribe(root -> apply(scene, scheme.getValue()));
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
