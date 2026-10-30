package nl.enadeag.carchargeplanner.desktop;

import nl.enadeag.carchargeplanner.desktop.dashboard.DashboardPictures;
import nl.enadeag.carchargeplanner.desktop.settings.SettingsPictures;
import nl.enadeag.carchargeplanner.desktop.settings.setup.SetUpPictures;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javafx.application.ColorScheme;
import javafx.scene.image.Image;

/**
 * Every picture the README and the pages in {@code docs/} show, each in light and in dark, as {@code <name>-light.png}
 * and {@code <name>-dark.png}. {@code ./mvnw test -Pscreenshots} makes them again in {@link #FOLDER} (CONTRIBUTING.md,
 * Pictures in the documentation).
 */
public final class ReadmePictures {

    /** Where the documentation's pictures are, from the repository's root. */
    public static final Path FOLDER = Path.of("docs", "screenshots", "java");

    /** Makes one picture in a scheme, with an empty folder for the files of the example home. */
    interface Picture {

        Image take(ColorScheme scheme, Path work) throws Exception;
    }

    /** The pictures by name. */
    static final Map<String, Picture> PICTURES = Map.of(
            "charts", DashboardPictures::charts,
            "setup-welcome-english", SetUpPictures::setUpWelcome,
            "setup-location-english", SetUpPictures::setUpLocation,
            "setup-preview-english", SetUpPictures::setUpPreview,
            "dashboard-200", DashboardPictures::at200,
            "dashboard-trip", DashboardPictures::trip,
            "settings-200", SettingsPictures::settingsAt200);

    private ReadmePictures() {
    }

    /**
     * Makes every picture in light and dark into a folder, and writes the ones whose pixels changed.
     *
     * @param into where the pictures go
     * @param work an empty folder for the example home's files
     * @return the names of the files written, such as {@code charts-dark.png}; empty when no picture changed
     * @throws Exception when a picture cannot be made or written
     */
    public static List<String> make(Path into, Path work) throws Exception {
        var written = new ArrayList<String>();
        for (var picture : PICTURES.entrySet()) {
            for (var scheme : ColorScheme.values()) {
                var file = fileName(picture.getKey(), scheme);
                var folder = Files.createDirectory(work.resolve(file.replace(".png", "")));
                if (Pictures.write(picture.getValue().take(scheme, folder), into.resolve(file))) {
                    written.add(file);
                }
            }
        }
        return written.stream().sorted().toList();
    }

    /**
     * A picture's file name.
     *
     * @param name the picture's name, such as {@code charts}
     * @param scheme light or dark
     * @return such as {@code charts-dark.png}
     */
    static String fileName(String name, ColorScheme scheme) {
        return name + "-" + scheme.name().toLowerCase(Locale.ROOT) + ".png";
    }
}
