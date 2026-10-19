package nl.enadeag.carchargeplanner.engine.battery;

import nl.enadeag.carchargeplanner.engine.config.Percent;
import nl.enadeag.carchargeplanner.engine.store.FileFormat;
import nl.enadeag.carchargeplanner.engine.store.FileVersion;
import nl.enadeag.carchargeplanner.engine.store.FormatProblem;
import nl.enadeag.carchargeplanner.engine.store.Stored;
import nl.enadeag.carchargeplanner.engine.store.StoredFile;
import nl.enadeag.carchargeplanner.engine.store.StoredJson;
import java.io.IOException;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * The battery level the owner entered last, in a JSON file the engine keeps:
 * {@code {"version": 1, "levelPct": 64.5, "enteredAt": "2026-07-03T07:00:00Z"}}, where a file without a version is
 * version 1. A file that is missing, cannot be read or holds no valid level counts as no reading, so a bad file never
 * stops planning; it is kept as {@code battery.json.damaged-<time>} when a new level is saved. The reading is kept in
 * memory until the file changes on disk.
 */
public final class BatteryFile {

    /** The shape this app writes, and where the migrations from older shapes go: none yet, 1 is the first. */
    private static final FileVersion VERSION = new FileVersion(1, List.of());

    private final StoredFile<BatteryReading> file;

    /**
     * Makes the store for one file; nothing is read or written yet.
     *
     * @param path where the file is, or should be; it and its folder are made when the first level is saved
     */
    public BatteryFile(Path path) {
        this.file = new StoredFile<>(path, new Format());
    }

    /**
     * The level the owner entered last.
     *
     * @return the reading, or empty when none was entered or the file cannot be used
     */
    public Optional<BatteryReading> reading() {
        return file.read() instanceof Stored.Found<BatteryReading>(var reading) ? Optional.of(reading)
                : Optional.empty();
    }

    /**
     * Replaces the stored level.
     *
     * @param reading the level the owner entered and when
     * @throws IOException when the file could not be written, or a file that could not be used not renamed; it is
     *     then left as it was
     */
    public void save(BatteryReading reading) throws IOException {
        file.write(reading);
    }

    /** The JSON shape of a reading. */
    private static final class Format implements FileFormat<BatteryReading> {

        @Override
        public BatteryReading read(JsonNode root) throws FormatProblem {
            var values = VERSION.upToDate(root);
            var level = values.path("levelPct");
            var enteredAt = values.path("enteredAt");
            if (!level.isNumber() || !enteredAt.isString()) {
                throw new FormatProblem("levelPct must be a number and enteredAt a moment");
            }
            try {
                return new BatteryReading(new Percent(level.doubleValue()), Instant.parse(enteredAt.stringValue()));
            } catch (DateTimeException | IllegalArgumentException _) {
                throw new FormatProblem("levelPct must be from 0 to 100 and enteredAt a moment such as "
                        + "2026-07-03T07:00:00Z");
            }
        }

        @Override
        public ObjectNode write(BatteryReading reading) {
            return VERSION.stamped(StoredJson.object()
                    .put("levelPct", reading.level().value())
                    .put("enteredAt", reading.enteredAt().toString()));
        }
    }
}
