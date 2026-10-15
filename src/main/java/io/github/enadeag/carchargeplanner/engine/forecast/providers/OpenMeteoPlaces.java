package io.github.enadeag.carchargeplanner.engine.forecast.providers;

import io.github.enadeag.carchargeplanner.engine.config.Coordinates;
import io.github.enadeag.carchargeplanner.engine.forecast.HttpGet;
import io.github.enadeag.carchargeplanner.engine.forecast.Place;
import io.github.enadeag.carchargeplanner.engine.forecast.PlaceResults;
import io.github.enadeag.carchargeplanner.engine.forecast.PlaceResults.Problem;
import io.github.enadeag.carchargeplanner.engine.forecast.PlaceSearch;
import io.github.enadeag.carchargeplanner.engine.forecast.ProviderError;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import tools.jackson.databind.JsonNode;

/**
 * Open-Meteo's geocoding service as a place search. Two characters match a name exactly, three or more the start of a
 * name; the results are ranked by size and come with coordinates and a time zone.
 */
public final class OpenMeteoPlaces implements PlaceSearch {

    private static final String SERVER = "https://geocoding-api.open-meteo.com/v1/search";
    private static final int MIN_CHARACTERS = 2;
    private static final int RESULTS = 8;
    /** A person is waiting at a search box. */
    private static final Duration TIMEOUT = Duration.ofSeconds(6);

    private final HttpGet http;

    /**
     * Makes the place search.
     *
     * @param http the only way it reaches the network; tests pass recorded answers
     */
    public OpenMeteoPlaces(HttpGet http) {
        this.http = http;
    }

    @Override
    public PlaceResults search(String text, String language) {
        var name = String.join(" ", text.strip().split("\\s+"));
        if (name.length() < MIN_CHARACTERS) {
            return new PlaceResults.Failed(Problem.TOO_SHORT); // nothing is sent for it
        }
        HttpGet.Reply reply;
        try {
            reply = Exchange.send(http, new HttpGet.Request(requestUri(name, language), Map.of(), TIMEOUT), "Open-Meteo");
        } catch (ProviderError.Unavailable e) {
            return new PlaceResults.Failed(Problem.UNAVAILABLE);
        }
        return results(reply);
    }

    private static URI requestUri(String name, String language) {
        var shortLanguage = language.isBlank() ? "en" : language.split("_")[0].toLowerCase(Locale.ROOT);
        var query = Map.of("name", name, "count", String.valueOf(RESULTS), "language", shortLanguage, "format", "json");
        return URI.create(SERVER + "?" + query.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&")));
    }

    private static PlaceResults results(HttpGet.Reply reply) {
        if (reply.status() == 429) {
            return new PlaceResults.Failed(Problem.RATE_LIMITED, reply.retryAfter());
        }
        if (reply.status() >= 500) {
            return new PlaceResults.Failed(Problem.UNAVAILABLE);
        }
        var body = Exchange.json(reply.body());
        var rows = body.path("results");
        boolean understood = reply.ok() && body.isObject() && !body.path("error").asBoolean(false)
                && (rows.isMissingNode() || rows.isNull() || rows.isArray());
        if (!understood) {
            return new PlaceResults.Failed(Problem.BAD_RESPONSE);
        }
        var places = new ArrayList<Place>();
        rows.forEach(row -> place(row).ifPresent(places::add)); // no match is an answer without a results list
        return new PlaceResults.Found(places);
    }

    /** One result, or nothing when it lacks what a location needs: a name, coordinates and a known time zone. */
    private static Optional<Place> place(JsonNode row) {
        var latitude = row.path("latitude");
        var longitude = row.path("longitude");
        var name = text(row, "name");
        boolean complete = latitude.isNumber() && longitude.isNumber() && !name.isEmpty()
                && Coordinates.areValid(latitude.doubleValue(), longitude.doubleValue());
        if (!complete) {
            return Optional.empty();
        }
        var location = new Coordinates(latitude.doubleValue(), longitude.doubleValue());
        return zone(text(row, "timezone")).map(zone -> new Place(name, location, zone, text(row, "admin1"),
                country(row), text(row, "admin2")));
    }

    /** The country code stands in for a missing country name. */
    private static String country(JsonNode row) {
        var name = text(row, "country");
        return name.isEmpty() ? text(row, "country_code") : name;
    }

    private static Optional<ZoneId> zone(String id) {
        try {
            return Optional.of(ZoneId.of(id));
        } catch (DateTimeException e) {
            return Optional.empty();
        }
    }

    private static String text(JsonNode row, String field) {
        var value = row.path(field);
        return value.isString() ? value.stringValue() : "";
    }
}
