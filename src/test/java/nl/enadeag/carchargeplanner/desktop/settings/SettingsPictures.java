package nl.enadeag.carchargeplanner.desktop.settings;

import nl.enadeag.carchargeplanner.desktop.Fx;
import nl.enadeag.carchargeplanner.desktop.Pictures.Frame;
import nl.enadeag.carchargeplanner.desktop.Pictures.Studio;
import nl.enadeag.carchargeplanner.desktop.settings.page.Category;
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
 * The README's picture of Settings, over the example home of the tests, in English and at a time that never changes,
 * so the picture is the same on every run. Set-up's are in {@code SetUpPictures}.
 */
public final class SettingsPictures {

    /** A Friday in summer, mid-afternoon, the moment of set-up's pictures too. */
    private static final Clock CLOCK = Clock.fixed(LocalDate.of(2026, 7, 3).atTime(15, 20)
            .atZone(ZoneId.of("Europe/Amsterdam")).toInstant(), ZoneOffset.UTC);
    /** Settings' window as the app opens it: 56 by 40 lines. */
    private static final double SETTINGS_WIDTH = 56 * 13;
    private static final double SETTINGS_HEIGHT = 40 * 13;

    private SettingsPictures() {
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
                    settings.background, settings.messages);
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


}
