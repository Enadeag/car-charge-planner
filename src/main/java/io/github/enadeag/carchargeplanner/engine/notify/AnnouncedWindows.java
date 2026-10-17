package io.github.enadeag.carchargeplanner.engine.notify;

import io.github.enadeag.carchargeplanner.engine.plan.Window;
import io.github.enadeag.carchargeplanner.engine.store.PrivateFiles;
import io.github.enadeag.carchargeplanner.engine.store.StoredJson;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;

/**
 * The windows the owner has been told about, in a file, so a restart neither announces them again nor plans them
 * differently while they run. A file that cannot be read counts as empty; one that cannot be written does not stop
 * planning. The file is a list with an object per window, in this shape of its own:
 * {@code [{"start": "2026-07-03T10:00:00Z", "end": "2026-07-03T12:00:00Z", "amps": 16, "powerKw": 3.68,
 * "solarKwh": 6.9, "importKwh": 0.46}]}.
 */
public final class AnnouncedWindows {

    private final Path file;

    /**
     * Makes the store; nothing is read or written yet.
     *
     * @param file the file the windows are kept in; it and its folder are made when the first window is saved
     */
    public AnnouncedWindows(Path file) {
        this.file = file;
    }

    /**
     * The windows announced so far.
     *
     * @return the windows in the order they were announced; empty when there is no file or it cannot be read
     */
    public List<Window> load() {
        try {
            // a missing or damaged file: at worst a window is announced once more
            return windows(StoredJson.parse(Files.readString(file))).orElse(List.of());
        } catch (IOException | JacksonException e) {
            return List.of();
        }
    }

    /**
     * Replaces the windows in the file.
     *
     * @param windows the windows to keep, possibly none
     */
    public void save(List<Window> windows) {
        var rows = StoredJson.array();
        windows.forEach(window -> rows.addObject()
                .put("start", window.start().toString())
                .put("end", window.end().toString())
                .put("amps", window.amps())
                .put("powerKw", window.powerKw())
                .put("solarKwh", window.solarKwh())
                .put("importKwh", window.importKwh()));
        try {
            PrivateFiles.write(file, StoredJson.compact(rows));
        } catch (IOException e) {
            // the windows are kept for this run all the same: the announcer remembers them itself
        }
    }

    /** The windows, or empty when any row is not a whole window. */
    private static Optional<List<Window>> windows(JsonNode rows) {
        if (!rows.isArray()) {
            return Optional.empty();
        }
        var windows = new ArrayList<Window>();
        for (var row : rows.values()) {
            var start = instant(row.path("start"));
            var end = instant(row.path("end"));
            var amps = row.path("amps");
            if (start.isEmpty() || end.isEmpty() || !amps.isIntegralNumber() || !amps.canConvertToInt()
                    || !row.path("powerKw").isNumber() || !row.path("solarKwh").isNumber()
                    || !row.path("importKwh").isNumber()) {
                return Optional.empty();
            }
            windows.add(new Window(start.get(), end.get(), amps.intValue(), row.path("powerKw").doubleValue(),
                    row.path("solarKwh").doubleValue(), row.path("importKwh").doubleValue()));
        }
        return Optional.of(List.copyOf(windows));
    }

    private static Optional<Instant> instant(JsonNode stamp) {
        try {
            return stamp.isString() ? Optional.of(Instant.parse(stamp.stringValue())) : Optional.empty();
        } catch (DateTimeException e) {
            return Optional.empty();
        }
    }
}
