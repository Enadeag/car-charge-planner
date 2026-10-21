package nl.enadeag.carchargeplanner.engine.trip;

import nl.enadeag.carchargeplanner.engine.store.FileFormat;
import nl.enadeag.carchargeplanner.engine.store.FileVersion;
import nl.enadeag.carchargeplanner.engine.store.FormatProblem;
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
 * The trips the owner entered, in a JSON file the engine keeps:
 * {@code {"version": 1, "trips": [{"departure": "2026-07-04T06:00:00Z", "return": "2026-07-04T18:00:00Z",
 * "distanceKm": 300, "canChargeAtDestination": false}]}}, where a file without a version is version 1. A trip is
 * kept until a day after its return; an older one is left out when the file is read, and dropped from it at the next
 * change. A file that is missing or cannot be used holds none, and a damaged entry is left out, so a bad file never
 * stops planning. But nothing is added to, changed in or removed from such a file: that would lose what it holds, so
 * the owner is told instead. The trips are kept in memory until the file changes on disk.
 */
public final class TripFile {

    /** How long a trip is kept after its return, so the day it ended on can still show it. */
    static final Duration KEEP_AFTER_RETURN = Duration.ofDays(1);

    /** The shape this app writes, and where the migrations from older shapes go: none yet, 1 is the first. */
    private static final FileVersion VERSION = new FileVersion(1, List.of());

    private static final Comparator<Trip> BY_DEPARTURE = Comparator.comparing(Trip::departure);

    /** The trips in the file, and how many entries were left out as damaged. */
    private record Entries(List<Trip> trips, int damaged) {
    }

    private final StoredFile<Entries> file;

    /**
     * Makes the store for one file; nothing is read or written yet.
     *
     * @param path where the file is, or should be; it and its folder are made when the first trip is added
     */
    public TripFile(Path path) {
        this.file = new StoredFile<>(path, new Format());
    }

    /**
     * The stored trips that are not past.
     *
     * @param now the current moment, which decides which trips are past
     * @return the trips that returned less than a day before now or later, in order of their departure, each once;
     *     empty when the file is missing or cannot be used
     */
    public List<Trip> trips(Instant now) {
        return file.read() instanceof Stored.Found<Entries>(var entries) ? notPast(entries.trips(), now) : List.of();
    }

    /**
     * Stores one more trip; one that is stored already is kept once. Past trips are dropped at the same time.
     *
     * @param trip the trip to add
     * @param now the current moment, which decides which trips are past
     * @return the stored trips after the change, as {@link #trips(Instant)} gives them
     * @throws IOException when the file could not be written, or it exists but could not be read whole; it is then
     *     left as it was
     */
    public List<Trip> add(Trip trip, Instant now) throws IOException {
        return rewrite(List.of(trip), trip, now);
    }

    /**
     * Replaces a stored trip by its changed version. Past trips are dropped at the same time.
     *
     * @param trip the trip as it is stored; when it is not stored, the changed one is added all the same
     * @param changed the trip as the owner changed it
     * @param now the current moment, which decides which trips are past
     * @return the stored trips after the change, as {@link #trips(Instant)} gives them
     * @throws IOException when the file could not be written, or it exists but could not be read whole; it is then
     *     left as it was
     */
    public List<Trip> change(Trip trip, Trip changed, Instant now) throws IOException {
        return rewrite(List.of(trip, changed), changed, now);
    }

    /**
     * Forgets one trip. Past trips are dropped at the same time.
     *
     * @param trip the trip to remove; one that is not stored changes nothing else
     * @param now the current moment, which decides which trips are past
     * @return the stored trips after the change, as {@link #trips(Instant)} gives them
     * @throws IOException when the file could not be written, or it exists but could not be read whole; it is then
     *     left as it was
     */
    public List<Trip> remove(Trip trip, Instant now) throws IOException {
        var trips = notPast(storedForChange().stream().filter(stored -> !stored.equals(trip)).toList(), now);
        file.write(new Entries(trips, 0));
        return trips;
    }

    /** Writes the stored trips without the ones left out, with the one added, and without the past ones. */
    private List<Trip> rewrite(List<Trip> leftOut, Trip added, Instant now) throws IOException {
        var trips = new ArrayList<>(storedForChange().stream().filter(stored -> !leftOut.contains(stored)).toList());
        trips.add(added);
        var kept = notPast(trips, now);
        file.write(new Entries(kept, 0));
        return kept;
    }

    private static List<Trip> notPast(List<Trip> trips, Instant now) {
        var keepFrom = now.minus(KEEP_AFTER_RETURN);
        return trips.stream().filter(trip -> trip.returnTime().isAfter(keepFrom)).sorted(BY_DEPARTURE).toList();
    }

    /** The stored trips, when writing them back loses nothing. */
    private List<Trip> storedForChange() throws IOException {
        return switch (file.read()) {
            case Stored.Absent<Entries> _ -> List.of();
            case Stored.Unusable<Entries> unusable -> throw new IOException(
                    file.path() + " could not be read, so it is left as it is: " + unusable.detail());
            case Stored.Found<Entries>(var entries) when entries.damaged() > 0 -> throw new IOException(
                    file.path() + " holds " + entries.damaged() + " damaged entries, so it is left as it is");
            case Stored.Found<Entries>(var entries) -> entries.trips();
        };
    }

    /** The JSON shape of the trips. */
    private static final class Format implements FileFormat<Entries> {

        @Override
        public Entries read(JsonNode root) throws FormatProblem {
            var rows = VERSION.upToDate(root).path("trips");
            if (!rows.isArray()) {
                throw new FormatProblem("trips: must be a list");
            }
            var trips = new ArrayList<Trip>();
            int damaged = 0;
            for (var row : rows.values()) {
                var found = trip(row);
                if (found.isEmpty()) {
                    damaged++;
                } else if (!trips.contains(found.get())) {
                    trips.add(found.get());
                }
            }
            return new Entries(List.copyOf(trips), damaged);
        }

        @Override
        public ObjectNode write(Entries entries) {
            var body = StoredJson.object();
            var rows = body.putArray("trips");
            entries.trips().forEach(trip -> rows.addObject()
                    .put("departure", trip.departure().toString())
                    .put("return", trip.returnTime().toString())
                    .put("distanceKm", trip.distanceKm())
                    .put("canChargeAtDestination", trip.canChargeAtDestination()));
            return VERSION.stamped(body);
        }

        private static Optional<Trip> trip(JsonNode row) {
            var departure = row.path("departure");
            var back = row.path("return");
            var distance = row.path("distanceKm");
            var canCharge = row.path("canChargeAtDestination");
            if (!departure.isString() || !back.isString() || !distance.isNumber() || !canCharge.isBoolean()) {
                return Optional.empty();
            }
            try {
                return Optional.of(new Trip(Instant.parse(departure.stringValue()), Instant.parse(back.stringValue()),
                        distance.doubleValue(), canCharge.booleanValue()));
            } catch (DateTimeException | IllegalArgumentException _) {
                return Optional.empty();
            }
        }
    }
}
