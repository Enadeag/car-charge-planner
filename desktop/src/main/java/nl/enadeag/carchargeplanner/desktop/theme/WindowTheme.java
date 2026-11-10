package nl.enadeag.carchargeplanner.desktop.theme;

import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import nl.enadeag.carchargeplanner.desktop.system.TextSize;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import javafx.application.Application;
import javafx.application.ColorScheme;
import javafx.beans.value.ObservableValue;
import javafx.scene.Scene;
import javafx.util.Subscription;

/**
 * Light or dark and the system's text size for the scenes of all the app's windows (the dashboard, Settings and
 * set-up), as they are set, also when they change while the app runs.
 */
public final class WindowTheme {

    private static final String STYLESHEET = Objects.requireNonNull(WindowTheme.class.getResource("dashboard.css"),
            "dashboard.css is missing from the app").toExternalForm();
    /** AtlantaFX's font size for the root, tooltips and a text field's menu; the app sizes everything else in em. */
    private static final double NORMAL_FONT_SIZE = 14;

    private final ObservableValue<ColorScheme> scheme;
    private final ObservableValue<TextSize> textSize;
    private final List<Scene> scenes = new ArrayList<>();
    /** Follows the text size while the theme styles a scene, so a theme whose windows all closed can be collected. */
    private Subscription followingTextSize = Subscription.EMPTY;

    /**
     * Makes the theme, following the scheme and the text size from now on. Make it on the JavaFX thread.
     *
     * @param scheme the colour scheme to follow: the owner's appearance over the system's
     *     ({@link nl.enadeag.carchargeplanner.desktop.app.Appearance#scheme Appearance.scheme}); never null
     * @param textSize the system's text size, changed on the JavaFX thread; never null
     */
    public WindowTheme(ObservableValue<ColorScheme> scheme, ObservableValue<TextSize> textSize) {
        this.scheme = scheme;
        this.textSize = textSize;
        scheme.subscribe(now -> {
            // AtlantaFX's theme also has to reach tooltips, which are windows of their own: set it for the application
            Application.setUserAgentStylesheet(now == ColorScheme.DARK ? new PrimerDark().getUserAgentStylesheet()
                    : new PrimerLight().getUserAgentStylesheet());
            scenes.forEach(scene -> markDark(scene, now));
        });
    }

    /**
     * Gives a scene the windows' shared stylesheet, light or dark as the scheme is and with text as large as the
     * system's, now and whenever they change, also when the scene gets another root.
     *
     * @param scene the scene to style; the theme keeps it until {@link #forget(Scene)}
     */
    public void style(Scene scene) {
        scene.getStylesheets().add(STYLESHEET);
        addTextSize(scene, textSize.getValue());
        if (scenes.isEmpty()) {
            followingTextSize = textSize.subscribe((before, now) -> scenes.forEach(styled -> {
                styled.getStylesheets().remove(textSizeSheet(before));
                addTextSize(styled, now);
            }));
        }
        scenes.add(scene);
        scene.rootProperty().subscribe(_ -> markDark(scene, scheme.getValue()));
    }

    /**
     * Stops following the scheme and the text size for a scene whose window closed for good, so the theme no longer
     * holds on to it and it can be collected.
     *
     * @param scene a scene given to {@link #style(Scene)}; one that was not changes nothing
     */
    public void forget(Scene scene) {
        if (scenes.remove(scene) && scenes.isEmpty()) {
            followingTextSize.unsubscribe();
            followingTextSize = Subscription.EMPTY;
        }
    }

    /**
     * A size at the system's text size now, such as a window's size that is given in lines of text.
     *
     * @param normal the size at normal text size, in pixels
     * @return the size in pixels, larger by as much as the system's text
     */
    public double atTextSize(double normal) {
        return textSize.getValue().of(normal);
    }

    /** Gives the scene's root the class "dark" while the scheme is dark, which the stylesheet's dark colours need. */
    private static void markDark(Scene scene, ColorScheme scheme) {
        var classes = scene.getRoot().getStyleClass();
        classes.remove("dark");
        if (scheme == ColorScheme.DARK) {
            classes.add("dark");
        }
    }

    /**
     * Adds the stylesheet that makes the text larger, after the shared one. At normal size the scene gets none, so it
     * is styled exactly as before.
     */
    private static void addTextSize(Scene scene, TextSize size) {
        if (!size.equals(TextSize.NORMAL)) {
            scene.getStylesheets().add(textSizeSheet(size));
        }
    }

    /**
     * The stylesheet for a text size, as a data URL. A scene's stylesheet overrides AtlantaFX's, which is the user
     * agent's, and a popup window, such as a tooltip or a combo box's list, takes its owner scene's stylesheets when it
     * shows, which an inline style on the root would not reach.
     */
    private static String textSizeSheet(TextSize size) {
        var css = String.format(Locale.ROOT, ".root, .tooltip, .text-input .context-menu { -fx-font-size: %.2fpx; }",
                size.of(NORMAL_FONT_SIZE));
        return "data:text/css;base64," + Base64.getEncoder().encodeToString(css.getBytes(StandardCharsets.UTF_8));
    }
}
