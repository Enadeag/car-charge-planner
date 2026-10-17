package io.github.enadeag.carchargeplanner.engine.battery;

import io.github.enadeag.carchargeplanner.engine.store.StoredJson;
import io.github.enadeag.carchargeplanner.engine.store.PrivateFiles;
import io.github.enadeag.carchargeplanner.engine.config.Percent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.Optional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;

/**
 * The battery level the owner entered last, in a JSON file the engine keeps. A file that is missing, cannot be read or
 * holds no valid level counts as no reading, so a bad file never stops planning.
 */
public final class BatteryFile {

    private final Path path;

    /**
     * Makes the store for one file; nothing is read or written yet.
     *
     * @param path where the file is, or should be; it and its folder are made when the first level is saved
     */
    public BatteryFile(Path path) {
        this.path = path;
    }

    /**
     * The level the owner entered last.
     *
     * @return the reading, or empty when none was entered or the file cannot be read
     */
    public Optional<BatteryReading> reading() {
        JsonNode blob;
        try {
            blob = StoredJson.parse(Files.readString(path));
        } catch (IOException | JacksonException e) {
            return Optional.empty();
        }
        try {
            var level = blob.path("levelPct");
            if (!level.isNumber()) {
                return Optional.empty();
            }
            return Optional.of(new BatteryReading(new Percent(level.asDouble()),
                    Instant.parse(blob.path("enteredAt").asString())));
        } catch (DateTimeException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /**
     * Replaces the stored level.
     *
     * @param reading the level the owner entered and when
     * @throws IOException when the file could not be written; it is then left as it was
     */
    public void save(BatteryReading reading) throws IOException {
        var blob = StoredJson.object()
                .put("levelPct", reading.level().value())
                .put("enteredAt", reading.enteredAt().toString());
        PrivateFiles.write(path, StoredJson.print(blob));
    }
}
