package io.github.enadeag.carchargeplanner.engine.forecast;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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

    public record Cached(Instant fetchedAt, List<ForecastStep> steps) {

        public Duration age(Instant now) {
            return Duration.between(fetchedAt, now);
        }
    }

    private final Path folder;
    private final JsonMapper json = new JsonMapper();

    public ForecastCache(Path folder) {
        this.folder = folder;
    }

    /** What a provider's forecast depends on: the system, and the provider's settings that are not secret. */
    JsonNode key(SystemDescription system, Map<String, String> plainSettings) {
        var key = json.createObjectNode();
        key.put("latitude", system.latitude());
        key.put("longitude", system.longitude());
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
     */
    Optional<Cached> forecast(String providerId, JsonNode key, Instant now) {
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

    Optional<Instant> cooldownUntil(String providerId) {
        return instant(read(providerId).path("cooldownUntil"));
    }

    /** Also ends a rate limit: the provider answered. */
    void saveForecast(String providerId, JsonNode key, Instant fetchedAt, List<ForecastStep> steps) {
        var blob = json.createObjectNode();
        blob.set("key", key);
        blob.put("fetchedAt", fetchedAt.toString());
        var rows = blob.putArray("steps");
        steps.forEach(step -> rows.addArray().add(step.start().toString()).add(step.end().toString()).add(step.powerKw()));
        blob.putNull("cooldownUntil");
        write(providerId, blob);
    }

    /** Keeps the cached forecast: it stays usable while the provider refuses. */
    void saveCooldown(String providerId, Instant until) {
        var blob = read(providerId);
        blob.put("cooldownUntil", until.toString());
        write(providerId, blob);
    }

    private Path file(String providerId) {
        return folder.resolve("forecast-" + providerId + ".json");
    }

    private ObjectNode read(String providerId) {
        try {
            return json.readTree(Files.readString(file(providerId))) instanceof ObjectNode blob ? blob : json.createObjectNode();
        } catch (IOException | JacksonException e) {
            return json.createObjectNode();
        }
    }

    private void write(String providerId, ObjectNode blob) {
        var target = file(providerId);
        var temporary = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            Files.createDirectories(folder);
            Files.writeString(temporary, json.writeValueAsString(blob));
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
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
