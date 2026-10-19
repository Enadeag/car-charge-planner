package nl.enadeag.carchargeplanner.desktop.settings;

import nl.enadeag.carchargeplanner.desktop.Fx;
import nl.enadeag.carchargeplanner.desktop.Pictures.Frame;
import nl.enadeag.carchargeplanner.desktop.Pictures.Studio;
import nl.enadeag.carchargeplanner.desktop.text.Language;
import nl.enadeag.carchargeplanner.desktop.text.TextKey;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import javafx.application.ColorScheme;
import javafx.scene.image.WritableImage;

/**
 * The README's pictures of set-up and Settings, over the example home of the tests, in English and at a time that
 * never changes, so the pictures are the same on every run.
 */
public final class SettingsPictures {

    /** A Friday in summer, mid-afternoon: the preview's chart shows the sun with "now" on it. */
    private static final Clock CLOCK = Clock.fixed(LocalDate.of(2026, 7, 3).atTime(15, 20)
            .atZone(ZoneId.of("Europe/Amsterdam")).toInstant(), ZoneOffset.UTC);
    /** Set-up's window as the app opens it: 50 by 46 lines. */
    private static final double SETUP_WIDTH = 50 * 13;
    private static final double SETUP_HEIGHT = 46 * 13;
    /** Settings' window as the app opens it: 56 by 40 lines. */
    private static final double SETTINGS_WIDTH = 56 * 13;
    private static final double SETTINGS_HEIGHT = 40 * 13;

    private SettingsPictures() {
    }

    /**
     * Set-up's first step, for a first run.
     *
     * @param scheme light or dark
     * @param folder an empty folder for set-up's files
     * @return the picture
     * @throws Exception when the picture cannot be made
     */
    public static WritableImage setUpWelcome(ColorScheme scheme, Path folder) throws Exception {
        return setUp(scheme, folder, (setUp, view) -> { });
    }

    /**
     * Set-up's location step, with the made-up place searched by name and chosen.
     *
     * @param scheme light or dark
     * @param folder an empty folder for set-up's files
     * @return the picture
     * @throws Exception when the picture cannot be made
     */
    public static WritableImage setUpLocation(ColorScheme scheme, Path folder) throws Exception {
        return setUp(scheme, folder, SettingsPictures::choosePlace);
    }

    /**
     * Set-up's preview of the dashboard before Finish, planned for the made-up place with the played forecast.
     *
     * @param scheme light or dark
     * @param folder an empty folder for set-up's files
     * @return the picture
     * @throws Exception when the picture cannot be made
     */
    public static WritableImage setUpPreview(ColorScheme scheme, Path folder) throws Exception {
        return setUp(scheme, folder, (setUp, view) -> {
            choosePlace(setUp, view);
            Fx.run(() -> {
                view.show(SetUpStep.PREVIEW);
                view.lookup(".day-tabs").setStyle("-fx-open-tab-animation: none;"); // a growing tab is narrow on purpose
            });
            setUp.awaitPreview(view);
        });
    }

    /**
     * Settings on the Charger page at 200 % display scaling, the focus ring on the Phases field.
     *
     * @param scheme light or dark
     * @param folder an empty folder for the settings' files
     * @return the picture
     * @throws Exception when the picture cannot be made
     */
    public static WritableImage settingsAt200(ColorScheme scheme, Path folder) throws Exception {
        var settings = new SettingsFixture(folder, Language.ENGLISH, CLOCK);
        return Fx.call(() -> {
            var view = SettingsView.of(settings.stores(true), settings.facts(), uri -> { }, () -> { }, chosen -> { },
                    Optional.empty(), settings.background, settings.messages);
            view.reload();
            view.show(Category.CHARGER);
            var studio = new Studio(view, scheme, Frame.WINDOW, 2, SETTINGS_WIDTH, Optional.of(SETTINGS_HEIGHT));
            try {
                settings.control(view, TextKey.FIELD_PHASES).requestFocus();
                return studio.take();
            } finally {
                studio.close();
            }
        });
    }

    /** What to do on set-up before its picture is taken, off the JavaFX thread. */
    private interface Steps {

        void walk(SetUpFixture setUp, SetUpView view) throws Exception;
    }

    /** Set-up as it opens for a first run, walked through some steps, then taken a picture of. */
    private static WritableImage setUp(ColorScheme scheme, Path folder, Steps steps) throws Exception {
        var setUp = new SetUpFixture(folder, Language.ENGLISH, CLOCK);
        var view = Fx.call(() -> SetUpView.of(setUp.stores(), setUp.keysFile(), uri -> { }, Fx.REDUCED_MOTION,
                () -> { }, chosen -> { }, setUp.background, setUp.messages));
        var studio = Fx.call(() -> {
            // shown first, so the step's first field, or Next, takes the focus as set-up's window opens
            var shown = new Studio(view, scheme, Frame.WINDOW, 1, SETUP_WIDTH, Optional.of(SETUP_HEIGHT));
            setUp.start(view);
            return shown;
        });
        try {
            steps.walk(setUp, view);
            return Fx.call(studio::take);
        } finally {
            Fx.run(studio::close);
        }
    }

    private static void choosePlace(SetUpFixture setUp, SetUpView view) throws Exception {
        Fx.run(() -> view.show(SetUpStep.LOCATION));
        setUp.choosePlace(view);
    }
}
