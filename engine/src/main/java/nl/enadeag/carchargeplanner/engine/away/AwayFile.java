package nl.enadeag.carchargeplanner.engine.away;

import nl.enadeag.carchargeplanner.engine.store.FileFormat;
import nl.enadeag.carchargeplanner.engine.store.FileVersion;
import nl.enadeag.carchargeplanner.engine.store.FormatProblem;
import nl.enadeag.carchargeplanner.engine.store.PrivateFiles;
import nl.enadeag.carchargeplanner.engine.store.Stored;
import nl.enadeag.carchargeplanner.engine.store.StoredFile;
import nl.enadeag.carchargeplanner.engine.store.StoredJson;
import java.io.IOException;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * The away periods the owner entered by hand for specific days, in a JSON file the engine keeps:
 * {@code {"version": 1, "periods": [{"start": "2026-07-04T06:00:00Z", "end": "2026-07-04T16:00:00Z"}]}}, where a file
 * without a version is version 1. A file that is missing or cannot be used holds none, and a damaged entry is left
 * out, so a bad file never stops planning. But nothing is added to or removed from such a file: that would lose what
 * it holds, so the owner is told instead. The periods are kept in memory until the file changes on disk.
 */
public final class AwayFile {

    /** How long a period is kept after it ended. */
    static final Duration KEEP_AFTER_END = Duration.ofDays(1);

    /** The shape this app writes, and where the migrations from older shapes go: none yet, 1 is the first. */
    private static final FileVersion VERSION = new FileVersion(1, List.of());

    /** The periods in the file, and how many entries were left out as damaged. */
    private record Entries(List<OneOffAwayPeriod> periods, int damaged) {
    }

    private final StoredFile<Entries> file;

    /**
     * Makes the store for one file; nothing is read or written yet.
     *
     * @param path where the file is, or should be; it and its folder are made when the first period is added
     * @param files how the file and its folders are written privately on this platform
     */
    public AwayFile(Path path, PrivateFiles files) {
        this.file = new StoredFile<>(path, new Format(), files);
    }

    /**
     * The stored periods.
     *
     * @return the periods in order of their start, each once; empty when the file is missing or cannot be used
     */
    public List<OneOffAwayPeriod> periods() {
        return file.read().orElse(new Entries(List.of(), 0)).periods();
    }

    /**
     * Stores one more period; one that is stored already is kept once. Periods that ended more than a day before now
     * are dropped at the same time.
     *
     * @param period the period to add
     * @param now the current moment, which decides which periods are old
     * @return the stored periods after the change, in order of their start
     * @throws IOException when the file could not be written, or it exists but could not be read whole; it is then
     *     left as it was
     */
    public List<OneOffAwayPeriod> add(OneOffAwayPeriod period, Instant now) throws IOException {
        var keepFrom = now.minus(KEEP_AFTER_END);
        var periods = new ArrayList<>(storedForChange().stream()
                .filter(stored -> stored.end().isAfter(keepFrom) && !stored.equals(period))
                .toList());
        periods.add(period);
        periods.sort(Comparator.comparing(OneOffAwayPeriod::start));
        file.write(new Entries(List.copyOf(periods), 0));
        return List.copyOf(periods);
    }

    /**
     * Forgets one period.
     *
     * @param period the period to remove; one that is not stored changes nothing
     * @return the stored periods after the change, in order of their start
     * @throws IOException when the file could not be written, or it exists but could not be read whole; it is then
     *     left as it was
     */
    public List<OneOffAwayPeriod> remove(OneOffAwayPeriod period) throws IOException {
        var periods = storedForChange().stream().filter(stored -> !stored.equals(period)).toList();
        file.write(new Entries(periods, 0));
        return periods;
    }

    /** The stored periods, when writing them back loses nothing. */
    private List<OneOffAwayPeriod> storedForChange() throws IOException {
        var stored = file.read();
        if (stored instanceof Stored.Absent<?>) {
            return List.of();
        } else if (stored instanceof Stored.Unusable<Entries> unusable) {
            throw new IOException(
                    file.path() + " could not be read, so it is left as it is: " + unusable.detail());
        } else if (stored instanceof Stored.Found<Entries> found && found.value().damaged() > 0) {
            throw new IOException(
                    file.path() + " holds " + found.value().damaged() + " damaged entries, so it is left as it is");
        } else if (stored instanceof Stored.Found<Entries> found) {
            return found.value().periods();
        } else {
            throw new IllegalStateException("unknown kind of stored value: " + stored);
        }
    }

    /** The JSON shape of the periods. */
    private static final class Format implements FileFormat<Entries> {

        @Override
        public Entries read(JsonNode root) throws FormatProblem {
            var rows = VERSION.upToDate(root).path("periods");
            if (!rows.isArray()) {
                throw new FormatProblem("periods: must be a list");
            }
            var periods = new ArrayList<OneOffAwayPeriod>();
            int damaged = 0;
            for (var row : rows.values()) {
                var found = period(row);
                if (found.isEmpty()) {
                    damaged++;
                } else if (!periods.contains(found.get())) {
                    periods.add(found.get());
                }
            }
            periods.sort(Comparator.comparing(OneOffAwayPeriod::start));
            return new Entries(List.copyOf(periods), damaged);
        }

        @Override
        public ObjectNode write(Entries entries) {
            var body = StoredJson.object();
            var rows = body.putArray("periods");
            entries.periods().forEach(period -> rows.addObject()
                    .put("start", period.start().toString())
                    .put("end", period.end().toString()));
            return VERSION.stamped(body);
        }

        private static Optional<OneOffAwayPeriod> period(JsonNode row) {
            var start = row.path("start");
            var end = row.path("end");
            if (!start.isString() || !end.isString()) {
                return Optional.empty();
            }
            try {
                return Optional.of(new OneOffAwayPeriod(Instant.parse(start.stringValue()),
                        Instant.parse(end.stringValue())));
            } catch (DateTimeException | IllegalArgumentException ignored) {
                return Optional.empty();
            }
        }
    }
}
