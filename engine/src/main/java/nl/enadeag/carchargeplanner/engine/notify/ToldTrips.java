package nl.enadeag.carchargeplanner.engine.notify;

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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * What the owner has been told about each trip, in a file, so a restart does not tell it again:
 * {@code {"version": 1, "trips": [{"departure": "2026-07-05T06:30:00Z", "chargeStart": true, "askedGridPct": 23.5,
 * "short": false}]}}, where a file without a version is version 1 and {@code askedGridPct} is left out while the owner
 * was not asked about the battery level. A file that cannot be used counts as empty, and is kept as
 * {@code trip-notices.json.damaged-<time>} when the trips are saved; one that cannot be written does not stop planning.
 */
public final class ToldTrips {

    /** The shape this app writes, and where the migrations from older shapes go: none yet, 1 is the first. */
    private static final FileVersion VERSION = new FileVersion(1, List.of());

    private final StoredFile<List<ToldTrip>> file;

    /**
     * Makes the store; nothing is read or written yet.
     *
     * @param file the file the trips are kept in; it and its folder are made when the first trip is saved
     */
    public ToldTrips(Path file) {
        this.file = new StoredFile<>(file, new Format());
    }

    /**
     * What the owner has been told so far.
     *
     * @return a record per trip; empty when there is no file or it cannot be used
     */
    public List<ToldTrip> load() {
        // a missing or damaged file: at worst a notice is told once more
        return file.read().orElse(List.of());
    }

    /**
     * Replaces the trips in the file. A file that cannot be written is left as it is, without an error: the announcer
     * keeps the trips for this run itself, and at worst a notice is told once more after a restart.
     *
     * @param trips the trips to keep, possibly none
     */
    public void save(List<ToldTrip> trips) {
        try {
            file.write(List.copyOf(trips));
        } catch (IOException ignored) {
            // the trips are kept for this run all the same: the announcer remembers them itself
        }
    }

    /** The JSON shape of the trips. */
    private static final class Format implements FileFormat<List<ToldTrip>> {

        @Override
        public List<ToldTrip> read(JsonNode root) throws FormatProblem {
            var rows = VERSION.upToDate(root).path("trips");
            if (!rows.isArray()) {
                throw new FormatProblem("trips: must be a list");
            }
            var trips = new ArrayList<ToldTrip>();
            for (var row : rows.values()) {
                var departure = departure(row.path("departure"));
                var asked = row.path("askedGridPct");
                if (departure.isEmpty() || !row.path("chargeStart").isBoolean() || !row.path("short").isBoolean()
                        || !(asked.isMissingNode() || asked.isNumber())) {
                    throw new FormatProblem("trip " + (trips.size() + 1) + " is not a whole trip");
                }
                trips.add(new ToldTrip(departure.get(), row.path("chargeStart").booleanValue(),
                        asked.isNumber() ? Optional.of(asked.doubleValue()) : Optional.empty(),
                        row.path("short").booleanValue()));
            }
            return List.copyOf(trips);
        }

        @Override
        public ObjectNode write(List<ToldTrip> trips) {
            var body = StoredJson.object();
            var rows = body.putArray("trips");
            trips.forEach(trip -> {
                var row = rows.addObject()
                        .put("departure", trip.departure().toString())
                        .put("chargeStart", trip.chargeStartTold());
                trip.askedGridPct().ifPresent(pct -> row.put("askedGridPct", pct));
                row.put("short", trip.shortTold());
            });
            return VERSION.stamped(body);
        }

        private static Optional<Instant> departure(JsonNode stamp) {
            try {
                return stamp.isString() ? Optional.of(Instant.parse(stamp.stringValue())) : Optional.empty();
            } catch (DateTimeException ignored) {
                return Optional.empty();
            }
        }
    }
}
