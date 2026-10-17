package io.github.enadeag.carchargeplanner.engine.away;

import io.github.enadeag.carchargeplanner.engine.store.StoredJson;
import io.github.enadeag.carchargeplanner.engine.store.PrivateFiles;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;

/**
 * The away periods the owner entered by hand for specific days, in a JSON file the engine keeps. A file that is
 * missing or cannot be read holds none, and a damaged entry is left out, so a bad file never stops planning.
 */
public final class AwayFile {

    /** How long a period is kept after it ended. */
    static final Duration KEEP_AFTER_END = Duration.ofDays(1);

    private final Path path;

    /**
     * Makes the store for one file; nothing is read or written yet.
     *
     * @param path where the file is, or should be; it and its folder are made when the first period is added
     */
    public AwayFile(Path path) {
        this.path = path;
    }

    /**
     * The stored periods.
     *
     * @return the periods in order of their start, each once; empty when the file is missing or cannot be read
     */
    public List<AwayPeriod> periods() {
        JsonNode blob;
        try {
            blob = StoredJson.parse(Files.readString(path));
        } catch (IOException | JacksonException e) {
            return List.of();
        }
        var periods = new ArrayList<AwayPeriod>();
        for (var entry : blob.path("periods")) {
            period(entry).filter(found -> !periods.contains(found)).ifPresent(periods::add);
        }
        periods.sort(Comparator.comparing(AwayPeriod::start));
        return periods;
    }

    /**
     * Stores one more period; one that is stored already is kept once. Periods that ended more than a day before now
     * are dropped at the same time.
     *
     * @param period the period to add
     * @param now the current moment, which decides which periods are old
     * @return the stored periods after the change, in order of their start
     * @throws IOException when the file could not be written; it is then left as it was
     */
    public List<AwayPeriod> add(AwayPeriod period, Instant now) throws IOException {
        var keepFrom = now.minus(KEEP_AFTER_END);
        var periods = new ArrayList<>(periods().stream()
                .filter(stored -> stored.end().isAfter(keepFrom) && !stored.equals(period))
                .toList());
        periods.add(period);
        periods.sort(Comparator.comparing(AwayPeriod::start));
        write(periods);
        return periods;
    }

    /**
     * Forgets one period.
     *
     * @param period the period to remove; one that is not stored changes nothing
     * @return the stored periods after the change, in order of their start
     * @throws IOException when the file could not be written; it is then left as it was
     */
    public List<AwayPeriod> remove(AwayPeriod period) throws IOException {
        var periods = periods().stream().filter(stored -> !stored.equals(period)).toList();
        write(periods);
        return periods;
    }

    private void write(List<AwayPeriod> periods) throws IOException {
        var blob = StoredJson.object();
        var rows = blob.putArray("periods");
        periods.forEach(period -> rows.addObject()
                .put("start", period.start().toString())
                .put("end", period.end().toString()));
        PrivateFiles.write(path, StoredJson.print(blob));
    }

    private static Optional<AwayPeriod> period(JsonNode entry) {
        try {
            return Optional.of(new AwayPeriod(Instant.parse(entry.path("start").asString()),
                    Instant.parse(entry.path("end").asString())));
        } catch (DateTimeException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
