package nl.enadeag.carchargeplanner.desktop.theme;

import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javafx.application.Application;
import javafx.application.ColorScheme;
import javafx.beans.value.ObservableValue;
import javafx.scene.Scene;

/**
 * Light or dark for the scenes of all the app's windows (the dashboard, Settings and set-up), as the colour scheme is
 * set, also when it changes while the app runs.
 */
public final class WindowTheme {

    private static final String STYLESHEET = Objects.requireNonNull(WindowTheme.class.getResource("dashboard.css"),
            "dashboard.css is missing from the app").toExternalForm();

    private final ObservableValue<ColorScheme> scheme;
    private final List<Scene> scenes = new ArrayList<>();

    /**
     * Makes the theme, following the scheme from now on.
     *
     * @param scheme the colour scheme to follow: the owner's appearance over the system's
     *     ({@link nl.enadeag.carchargeplanner.desktop.app.Appearance#scheme Appearance.scheme}); never null
     */
    public WindowTheme(ObservableValue<ColorScheme> scheme) {
        this.scheme = scheme;
        scheme.subscribe(now -> {
            // AtlantaFX's theme also has to reach tooltips, which are windows of their own: set it for the application
            Application.setUserAgentStylesheet(now == ColorScheme.DARK ? new PrimerDark().getUserAgentStylesheet()
                    : new PrimerLight().getUserAgentStylesheet());
            scenes.forEach(scene -> markDark(scene, now));
        });
    }

    /**
     * Gives a scene the windows' shared stylesheet, light or dark as the scheme is, now and whenever it changes, also
     * when the scene gets another root.
     *
     * @param scene the scene to style; the theme keeps it until {@link #forget(Scene)}
     */
    public void style(Scene scene) {
        scene.getStylesheets().add(STYLESHEET);
        scenes.add(scene);
        scene.rootProperty().subscribe(_ -> markDark(scene, scheme.getValue()));
    }

    /**
     * Stops following the scheme for a scene whose window closed for good, so the theme no longer holds on to it and
     * it can be collected.
     *
     * @param scene a scene given to {@link #style(Scene)}; one that was not changes nothing
     */
    public void forget(Scene scene) {
        scenes.remove(scene);
    }

    /** Gives the scene's root the class "dark" while the scheme is dark, which the stylesheet's dark colours need. */
    private static void markDark(Scene scene, ColorScheme scheme) {
        var classes = scene.getRoot().getStyleClass();
        classes.remove("dark");
        if (scheme == ColorScheme.DARK) {
            classes.add("dark");
        }
    }
}
