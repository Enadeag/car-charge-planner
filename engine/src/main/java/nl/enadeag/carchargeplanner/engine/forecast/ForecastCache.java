package nl.enadeag.carchargeplanner.engine.forecast;

import nl.enadeag.carchargeplanner.engine.config.ProviderId;
import nl.enadeag.carchargeplanner.engine.store.FileFormat;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.MissingNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * The last good forecast of each provider and the end of its cool-down, in a file per provider, so both survive a
 * restart. A file that cannot be read, or holds a forecast for another system, is not used; a folder that cannot be
 * written does not stop planning. Each file is kept in memory until it changes on disk.
 */
public final class ForecastCache {

    /**
     * A cached forecast.
     *
     * @param fetchedAt when it was fetched
     * @param steps its steps
     */
    public record Cached(Instant fetchedAt, List<ForecastStep> steps) {

        /**
         * How old the forecast is.
         *
         * @param now the moment to measure to; not before {@code fetchedAt}
         * @return the time since it was fetched
         */
        public Duration age(Instant now) {
            return Duration.between(fetchedAt, now);
        }
    }

    /** What one provider's file holds; a part that is missing or damaged is empty. */
    private record Entry(JsonNode key, Optional<Cached> forecast, Optional<Instant> cooldownUntil) {

        static final Entry NONE = new Entry(MissingNode.getInstance(), Optional.empty(), Optional.empty());
    }

    private final Path folder;
    private final Map<ProviderId, StoredFile<Entry>> files = new ConcurrentHashMap<>();

    /**
     * Makes the cache; nothing is read or written yet.
     *
     * @param folder where the files go; made when the first one is written
     */
    public ForecastCache(Path folder) {
        this.folder = folder;
    }

    /**
     * What a provider's forecast depends on, to tell whether a cached one still applies.
     *
     * @param system the system forecast for; the arrays' names are left out
     * @param plainSettings the provider's declared settings that are not secret; a changed key does not count
     * @return a JSON object that equals the one stored with a forecast for the same system and settings
     */
    JsonNode key(SolarSite system, Map<String, String> plainSettings) {
        var key = StoredJson.object();
        key.put("latitude", system.location().latitude());
        key.put("longitude", system.location().longitude());
        key.put("timezone", system.timezone().getId());
        var arrays = key.putArray("arrays"); // the arrays' names are left out: a forecast does not depend on them
        system.arrays().forEach(array -> arrays.addObject()
                .put("kwp", array.kwp())
                .put("tiltDeg", array.tiltDeg())
                .put("azimuthDeg", array.azimuthDeg())
                .put("systemLosses", array.systemLosses()));
        var settings = key.putObject("settings");
        new TreeMap<>(plainSettings).forEach(settings::put);
        return key;
    }

    /**
     * The cached forecast for this key, if it is intact. A forecast stamped after {@code now} is not used: the clock
     * was set back since, so its age is unknown and it could otherwise pass for fresh far too long.
     *
     * @param providerId the provider whose cache to read
     * @param key what the forecast must have been made for, as {@link #key} makes it
     * @param now the current moment
     * @return the forecast, or empty when there is none, it is for another key, it is damaged or stamped after now
     */
    Optional<Cached> forecast(ProviderId providerId, JsonNode key, Instant now) {
        var entry = read(providerId);
        if (!key.equals(entry.key())) {
            return Optional.empty();
        }
        return entry.forecast().filter(cached -> !cached.fetchedAt().isAfter(now));
    }

    /**
     * When the provider's last cool-down ends, which may have passed already.
     *
     * @param providerId the provider whose cache to read
     * @return the end, or empty when there is none or the file cannot be read
     */
    Optional<Instant> cooldownUntil(ProviderId providerId) {
        return read(providerId).cooldownUntil();
    }

    /**
     * Keeps a new forecast, replacing the provider's last one. Also ends a cool-down: the provider answered.
     *
     * @param providerId the provider that made it
     * @param key what it was made for, as {@link #key} makes it
     * @param fetchedAt when it was fetched
     * @param steps its steps, keeping the providers' contract
     */
    void saveForecast(ProviderId providerId, JsonNode key, Instant fetchedAt, List<ForecastStep> steps) {
        write(providerId, new Entry(key, Optional.of(new Cached(fetchedAt, List.copyOf(steps))), Optional.empty()));
    }

    /**
     * Notes a cool-down, keeping the cached forecast: it stays usable while the provider refuses.
     *
     * @param providerId the provider that refused
     * @param until when it may be asked again
     */
    void saveCooldown(ProviderId providerId, Instant until) {
        var entry = read(providerId);
        write(providerId, new Entry(entry.key(), entry.forecast(), Optional.of(until)));
    }

    private StoredFile<Entry> file(ProviderId providerId) {
        return files.computeIfAbsent(providerId,
                id -> new StoredFile<>(folder.resolve("forecast-" + id.value() + ".json"), new Format()));
    }

    private Entry read(ProviderId providerId) {
        return file(providerId).read().orElse(Entry.NONE);
    }

    private void write(ProviderId providerId, Entry entry) {
        try {
            file(providerId).write(entry);
        } catch (IOException ignored) {
            // a cache that cannot be written must not stop planning; the forecast is simply asked for again later
        }
    }

    /** The JSON shape of one provider's file: the key, the forecast's moment and steps, and the cool-down. */
    private static final class Format implements FileFormat<Entry> {

        @Override
        public Entry read(JsonNode root) throws FormatProblem {
            if (!root.isObject()) {
                throw new FormatProblem("the file must hold one JSON object");
            }
            var fetchedAt = instant(root.path("fetchedAt"));
            var steps = steps(root.path("steps"));
            var forecast = fetchedAt.isPresent() && steps.isPresent()
                    ? Optional.of(new Cached(fetchedAt.get(), steps.get())) : Optional.<Cached>empty();
            return new Entry(root.path("key"), forecast, instant(root.path("cooldownUntil")));
        }

        @Override
        public ObjectNode write(Entry entry) {
            var blob = StoredJson.object();
            if (!entry.key().isMissingNode()) {
                blob.set("key", entry.key());
            }
            entry.forecast().ifPresent(cached -> {
                blob.put("fetchedAt", cached.fetchedAt().toString());
                var rows = blob.putArray("steps");
                cached.steps().forEach(step -> rows.addArray().add(step.start().toString()).add(step.end().toString())
                        .add(step.powerKw()));
            });
            entry.cooldownUntil().ifPresentOrElse(until -> blob.put("cooldownUntil", until.toString()),
                    () -> blob.putNull("cooldownUntil"));
            return blob;
        }
    }

    private static Optional<Instant> instant(JsonNode stamp) {
        try {
            return stamp.isString() ? Optional.of(Instant.parse(stamp.stringValue())) : Optional.empty();
        } catch (DateTimeException ignored) {
            return Optional.empty();
        }
    }

    /** The steps, if every row is intact and they keep the providers' contract. */
    private static Optional<List<ForecastStep>> steps(JsonNode rows) {
        var steps = new ArrayList<ForecastStep>();
        for (var row : rows) {
            var start = instant(row.path(0));
            var end = instant(row.path(1));
            if (start.isEmpty() || end.isEmpty() || !row.path(2).isNumber()) {
                return Optional.empty();
            }
            steps.add(new ForecastStep(start.get(), end.get(), row.path(2).doubleValue()));
        }
        return steps.isEmpty() || !StepRules.problems(steps).isEmpty() ? Optional.empty() : Optional.of(steps);
    }
}
