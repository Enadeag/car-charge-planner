package io.github.enadeag.carchargeplanner.desktop;

import java.io.IOException;
import java.lang.System.Logger.Level;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * The file that keeps the {@link AppSettings}, in JSON, such as {@code {"language": "nl"}}. {@code "system"}, or no
 * {@code language} at all, follows the system's language. The app always starts: a file it cannot use is logged and
 * the settings follow the system.
 */
public final class AppSettingsFile {

    private static final System.Logger LOG = System.getLogger(AppSettingsFile.class.getName());
    private static final String FOLLOW_SYSTEM = "system";

    private final Path path;
    private final JsonMapper json = JsonMapper.builder().build();

    /**
     * Makes the reader for one file; nothing is read yet.
     *
     * @param path where the file is, or would be; it need not exist
     */
    public AppSettingsFile(Path path) {
        this.path = path;
    }

    /**
     * Reads the file again.
     *
     * @return the settings in the file; {@link AppSettings#SYSTEM} for any setting the file does not hold or holds
     *     wrongly, and for a file that is missing or not JSON
     */
    public AppSettings read() {
        String text;
        try {
            text = Files.readString(path);
        } catch (NoSuchFileException e) {
            return AppSettings.SYSTEM;
        } catch (IOException e) {
            LOG.log(Level.WARNING, "could not read " + path + ", so the app follows the system: " + e.getMessage());
            return AppSettings.SYSTEM;
        }
        try {
            var language = json.readTree(text).path("language").asString(FOLLOW_SYSTEM);
            if (language.equals(FOLLOW_SYSTEM)) {
                return AppSettings.SYSTEM;
            }
            var chosen = Language.ofCode(language);
            if (chosen.isEmpty()) {
                LOG.log(Level.WARNING, path + ": the app has no language \"" + language + "\", so it follows the system");
            }
            return new AppSettings(chosen);
        } catch (JacksonException e) {
            LOG.log(Level.WARNING, path + " is not JSON, so the app follows the system: " + e.getOriginalMessage());
            return AppSettings.SYSTEM;
        }
    }
}
