package io.github.enadeag.carchargeplanner.engine.forecast;

import io.github.enadeag.carchargeplanner.engine.config.ProviderId;
import io.github.enadeag.carchargeplanner.engine.store.PrivateFiles;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * The last good forecast of each provider and the end of its rate limit, in a file per provider, so both survive a
 * restart. A file that cannot be read, or holds a forecast for another system, is not used; a folder that cannot be
 * written does not stop planning.
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

    private final Path folder;
    private final JsonMapper json = new JsonMapper();

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
    JsonNode key(SystemDescription system, Map<String, String> plainSettings) {
        var key = json.createObjectNode();
        key.put("latitude", system.location().latitude());
        key.put("longitude", system.location().longitude());
        key.put("timezone", system.timezone().getId());
        var arrays = key.putArray("arrays"); // the arrays' names are left out: a forecast does not depend on them
        system.arrays().forEach(array -> arrays.addObject()
                .put("kwp", array.kwp())
                .put("tiltDeg", array.tiltDeg())
                .put("azimuthDeg", array.azimuthDeg())
                .put("systemLosses", array.systemLosses()));
        key.set("settings", json.valueToTree(new TreeMap<>(plainSettings)));
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
        var blob = read(providerId);
        if (!key.equals(blob.path("key"))) {
            return Optional.empty();
        }
        var fetchedAt = instant(blob.path("fetchedAt")).filter(stamp -> !stamp.isAfter(now));
        var steps = steps(blob.path("steps"));
        if (fetchedAt.isEmpty() || steps.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new Cached(fetchedAt.get(), steps.get()));
    }

    /**
     * When the provider's last rate limit ends, which may have passed already.
     *
     * @param providerId the provider whose cache to read
     * @return the end, or empty when there is none or the file cannot be read
     */
    Optional<Instant> cooldownUntil(ProviderId providerId) {
        return instant(read(providerId).path("cooldownUntil"));
    }

    /**
     * Keeps a new forecast, replacing the provider's last one. Also ends a rate limit: the provider answered.
     *
     * @param providerId the provider that made it
     * @param key what it was made for, as {@link #key} makes it
     * @param fetchedAt when it was fetched
     * @param steps its steps, keeping the providers' contract
     */
    void saveForecast(ProviderId providerId, JsonNode key, Instant fetchedAt, List<ForecastStep> steps) {
        var blob = json.createObjectNode();
        blob.set("key", key);
        blob.put("fetchedAt", fetchedAt.toString());
        var rows = blob.putArray("steps");
        steps.forEach(step -> rows.addArray().add(step.start().toString()).add(step.end().toString()).add(step.powerKw()));
        blob.putNull("cooldownUntil");
        write(providerId, blob);
    }

    /**
     * Notes a rate limit, keeping the cached forecast: it stays usable while the provider refuses.
     *
     * @param providerId the provider that refused
     * @param until when it may be asked again
     */
    void saveCooldown(ProviderId providerId, Instant until) {
        var blob = read(providerId);
        blob.put("cooldownUntil", until.toString());
        write(providerId, blob);
    }

    private Path file(ProviderId providerId) {
        return folder.resolve("forecast-" + providerId.value() + ".json");
    }

    private ObjectNode read(ProviderId providerId) {
        try {
            return json.readTree(Files.readString(file(providerId))) instanceof ObjectNode blob ? blob : json.createObjectNode();
        } catch (IOException | JacksonException e) {
            return json.createObjectNode();
        }
    }

    private void write(ProviderId providerId, ObjectNode blob) {
        try {
            PrivateFiles.write(file(providerId), json.writeValueAsString(blob));
        } catch (IOException e) {
            // a cache that cannot be written must not stop planning; the forecast is simply asked for again later
        }
    }

    private static Optional<Instant> instant(JsonNode stamp) {
        try {
            return stamp.isString() ? Optional.of(Instant.parse(stamp.stringValue())) : Optional.empty();
        } catch (DateTimeException e) {
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
        return steps.isEmpty() || !Steps.problems(steps).isEmpty() ? Optional.empty() : Optional.of(steps);
    }
}
