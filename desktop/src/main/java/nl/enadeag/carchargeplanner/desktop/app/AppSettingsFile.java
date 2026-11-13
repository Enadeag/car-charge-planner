package nl.enadeag.carchargeplanner.desktop.app;

import nl.enadeag.carchargeplanner.desktop.text.Language;
import nl.enadeag.carchargeplanner.engine.store.FileFormat;
import nl.enadeag.carchargeplanner.engine.store.FileVersion;
import nl.enadeag.carchargeplanner.engine.store.FormatProblem;
import nl.enadeag.carchargeplanner.engine.store.PrivateFiles;
import nl.enadeag.carchargeplanner.engine.store.Stored;
import nl.enadeag.carchargeplanner.engine.store.StoredFile;
import nl.enadeag.carchargeplanner.engine.store.StoredJson;
import java.io.IOException;
import java.lang.System.Logger.Level;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * The file that keeps the {@link AppSettings}, in JSON, such as
 * {@code {"version": 1, "language": "nl", "units": "metric", "mode": "automatic", "appearance": "dark"}}.
 * {@code "system"}, or no {@code language} at all, follows the system's language; no {@code appearance} follows the
 * system's light or dark; a file without a version is version 1. The app always starts: a value it cannot use is
 * logged and replaced by the one that follows the system, and a file it cannot use at all, such as one that is not
 * JSON, is logged and kept as {@code app.json.damaged-<time>} when the settings are saved. A file of a newer version
 * is read as following the system and never written over: saving the settings then fails.
 */
public final class AppSettingsFile {

    private static final System.Logger LOG = System.getLogger(AppSettingsFile.class.getName());
    private static final String FOLLOW_SYSTEM = "system";

    /** The shape this app writes, and where the migrations from older shapes go: none yet, 1 is the first. */
    private static final FileVersion VERSION = new FileVersion(1, List.of());

    private final StoredFile<AppSettings> file;
    private final AppSettings system;

    /**
     * Makes the reader and writer for one file; nothing is read yet.
     *
     * @param path where the file is, or would be; it need not exist
     * @param systemUnits the units of the owner's country, for a file that does not choose any
     * @param files how the file and its folder are written, private, on this system
     * @param clock the time a file that cannot be used is renamed with; never null
     */
    public AppSettingsFile(Path path, Units systemUnits, PrivateFiles files, Clock clock) {
        this.file = new StoredFile<>(path, new Format(), files, clock);
        this.system = AppSettings.following(systemUnits);
    }

    /**
     * Where the file is, for the owner.
     *
     * @return the path this was made with
     */
    public Path path() {
        return file.path();
    }

    /**
     * The settings in the file, read again when it changed since it was last read or written.
     *
     * @return the settings in the file; for any setting the file does not hold or holds wrongly, and for a file that
     *     is missing, not JSON or of a newer version, the setting that follows the system
     */
    public AppSettings read() {
        return file.read() instanceof Stored.Found<AppSettings>(var value) ? value : system;
    }

    /**
     * Replaces the file by these settings: the file is never half written, and only the owner can read it.
     *
     * @param settings the settings to keep
     * @throws IOException when the file was written by a newer version, the file or its folder could not be written,
     *     or a file that could not be used not renamed; the file is then left as it was
     */
    public void write(AppSettings settings) throws IOException {
        file.write(settings);
    }

    private final class Format implements FileFormat<AppSettings> {

        @Override
        public AppSettings read(JsonNode root) throws FormatProblem {
            var values = VERSION.upToDate(root);
            var language = values.path("language").asString(FOLLOW_SYSTEM);
            return new AppSettings(
                    language.equals(FOLLOW_SYSTEM) ? Optional.empty() : value(values, "language", Language::ofCode),
                    value(values, "units", Units::ofCode).orElse(system.units()),
                    value(values, "mode", DisplayMode::ofCode).orElse(system.mode()),
                    value(values, "appearance", Appearance::ofCode).orElse(system.appearance()));
        }

        @Override
        public ObjectNode write(AppSettings settings) {
            return VERSION.stamped(StoredJson.object()
                    .put("language", settings.language().map(Language::code).orElse(FOLLOW_SYSTEM))
                    .put("units", settings.units().code())
                    .put("mode", settings.mode().code())
                    .put("appearance", settings.appearance().code()));
        }

        private <T> Optional<T> value(JsonNode values, String key, Function<String, Optional<T>> known) {
            if (!values.has(key)) {
                return Optional.empty();
            }
            var text = values.path(key).asString("");
            var found = known.apply(text);
            if (found.isEmpty()) {
                LOG.log(Level.WARNING, path() + ": the app has no " + key + " \"" + text + "\", so it follows the system");
            }
            return found;
        }
    }
}
