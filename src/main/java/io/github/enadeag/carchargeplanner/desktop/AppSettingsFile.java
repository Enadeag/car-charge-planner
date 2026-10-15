package io.github.enadeag.carchargeplanner.desktop;

import java.io.IOException;
import java.lang.System.Logger.Level;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.function.Function;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The file that keeps the {@link AppSettings}, in JSON, such as
 * {@code {"language": "nl", "units": "metric", "mode": "automatic", "appearance": "dark"}}. {@code "system"}, or no
 * {@code language} at all, follows the system's language; no {@code appearance} follows the system's light or dark. The app always starts: a value it cannot use is logged and replaced by the one that
 * follows the system.
 */
public final class AppSettingsFile {

    private static final System.Logger LOG = System.getLogger(AppSettingsFile.class.getName());
    private static final String FOLLOW_SYSTEM = "system";

    private final Path path;
    private final AppSettings system;
    private final JsonMapper json = JsonMapper.builder().build();

    /**
     * Makes the reader and writer for one file; nothing is read yet.
     *
     * @param path where the file is, or would be; it need not exist
     * @param systemUnits the units of the owner's country, for a file that does not choose any
     */
    public AppSettingsFile(Path path, Units systemUnits) {
        this.path = path;
        this.system = AppSettings.following(systemUnits);
    }

    /**
     * Where the file is, for the owner.
     *
     * @return the path this was made with
     */
    public Path path() {
        return path;
    }

    /**
     * Reads the file again.
     *
     * @return the settings in the file; for any setting the file does not hold or holds wrongly, and for a file that
     *     is missing or not JSON, the setting that follows the system
     */
    public AppSettings read() {
        String text;
        try {
            text = Files.readString(path);
        } catch (NoSuchFileException e) {
            return system;
        } catch (IOException e) {
            LOG.log(Level.WARNING, "could not read " + path + ", so the app follows the system: " + e.getMessage());
            return system;
        }
        try {
            var file = json.readTree(text);
            var language = file.path("language").asString(FOLLOW_SYSTEM);
            return new AppSettings(
                    language.equals(FOLLOW_SYSTEM) ? Optional.empty() : value(file, "language", Language::ofCode),
                    value(file, "units", Units::ofCode).orElse(system.units()),
                    value(file, "mode", DisplayMode::ofCode).orElse(system.mode()),
                    value(file, "appearance", Appearance::ofCode).orElse(system.appearance()));
        } catch (JacksonException e) {
            LOG.log(Level.WARNING, path + " is not JSON, so the app follows the system: " + e.getOriginalMessage());
            return system;
        }
    }

    /**
     * Replaces the file by these settings. The new file is written beside the old one and then moved over it, so the
     * file is never half written.
     *
     * @param settings the settings to keep
     * @throws IOException when the file or its folder could not be written; the file is then left as it was
     */
    public void write(AppSettings settings) throws IOException {
        var file = json.createObjectNode()
                .put("language", settings.language().map(Language::code).orElse(FOLLOW_SYSTEM))
                .put("units", settings.units().code())
                .put("mode", settings.mode().code())
                .put("appearance", settings.appearance().code());
        var temporary = path.resolveSibling(path.getFileName() + ".tmp");
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        Files.writeString(temporary, json.writerWithDefaultPrettyPrinter().writeValueAsString(file));
        Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    /** A value of the file the app knows, or empty (and logged when it was there but unknown). */
    private <T> Optional<T> value(JsonNode file, String key, Function<String, Optional<T>> known) {
        if (!file.has(key)) {
            return Optional.empty();
        }
        var text = file.path(key).asString("");
        var found = known.apply(text);
        if (found.isEmpty()) {
            LOG.log(Level.WARNING, path + ": the app has no " + key + " \"" + text + "\", so it follows the system");
        }
        return found;
    }
}
