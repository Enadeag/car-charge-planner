package nl.enadeag.carchargeplanner.engine.notify;

import nl.enadeag.carchargeplanner.engine.plan.Window;
import nl.enadeag.carchargeplanner.engine.store.FileFormat;
import nl.enadeag.carchargeplanner.engine.store.FormatProblem;
import nl.enadeag.carchargeplanner.engine.store.Stored;
import nl.enadeag.carchargeplanner.engine.store.StoredFile;
import nl.enadeag.carchargeplanner.engine.store.StoredJson;
import java.io.IOException;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import tools.jackson.databind.JsonNode;

/**
 * The windows the owner has been told about, in a file, so a restart neither announces them again nor plans them
 * differently while they run. A file that cannot be used counts as empty, and is kept as
 * {@code announced.json.damaged-<time>} when windows are saved; one that cannot be written does not stop planning. The
 * file is a list with an object per window, in this shape of its own:
 * {@code [{"start": "2026-07-03T10:00:00Z", "end": "2026-07-03T12:00:00Z", "amps": 16, "powerKw": 3.68,
 * "solarKwh": 6.9, "importKwh": 0.46}]}.
 */
public final class AnnouncedWindows {

    private final StoredFile<List<Window>> file;

    /**
     * Makes the store; nothing is read or written yet.
     *
     * @param file the file the windows are kept in; it and its folder are made when the first window is saved
     */
    public AnnouncedWindows(Path file) {
        this.file = new StoredFile<>(file, new Format());
    }

    /**
     * The windows announced so far.
     *
     * @return the windows in the order they were announced; empty when there is no file or it cannot be used
     */
    public List<Window> load() {
        // a missing or damaged file: at worst a window is announced once more
        return file.read().orElse(List.of());
    }

    /**
     * Replaces the windows in the file. A file that cannot be written is left as it is, without an error: the
     * announcer keeps the windows for this run itself, and at worst a window is announced once more after a restart.
     *
     * @param windows the windows to keep, possibly none
     */
    public void save(List<Window> windows) {
        try {
            file.write(List.copyOf(windows));
        } catch (IOException ignored) {
            // the windows are kept for this run all the same: the announcer remembers them itself
        }
    }

    /** The JSON shape of the windows. */
    private static final class Format implements FileFormat<List<Window>> {

        @Override
        public List<Window> read(JsonNode rows) throws FormatProblem {
            if (!rows.isArray()) {
                throw new FormatProblem("the file must hold a list of windows");
            }
            var windows = new ArrayList<Window>();
            for (var row : rows.values()) {
                var start = instant(row.path("start"));
                var end = instant(row.path("end"));
                var amps = row.path("amps");
                if (start.isEmpty() || end.isEmpty() || !amps.isIntegralNumber() || !amps.canConvertToInt()
                        || !row.path("powerKw").isNumber() || !row.path("solarKwh").isNumber()
                        || !row.path("importKwh").isNumber()) {
                    throw new FormatProblem("window " + (windows.size() + 1) + " is not a whole window");
                }
                windows.add(new Window(start.get(), end.get(), amps.intValue(), row.path("powerKw").doubleValue(),
                        row.path("solarKwh").doubleValue(), row.path("importKwh").doubleValue()));
            }
            return List.copyOf(windows);
        }

        @Override
        public JsonNode write(List<Window> windows) {
            var rows = StoredJson.array();
            windows.forEach(window -> rows.addObject()
                    .put("start", window.start().toString())
                    .put("end", window.end().toString())
                    .put("amps", window.amps())
                    .put("powerKw", window.powerKw())
                    .put("solarKwh", window.solarKwh())
                    .put("importKwh", window.importKwh()));
            return rows;
        }

        private static Optional<Instant> instant(JsonNode stamp) {
            try {
                return stamp.isString() ? Optional.of(Instant.parse(stamp.stringValue())) : Optional.empty();
            } catch (DateTimeException ignored) {
                return Optional.empty();
            }
        }
    }
}
