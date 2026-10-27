package nl.enadeag.carchargeplanner.engine.route.services;

import nl.enadeag.carchargeplanner.engine.calendar.CalendarSecrets;
import nl.enadeag.carchargeplanner.engine.config.Coordinates;
import nl.enadeag.carchargeplanner.engine.forecast.ByteSize;
import nl.enadeag.carchargeplanner.engine.forecast.HttpGet;
import nl.enadeag.carchargeplanner.engine.forecast.SettingFormat;
import nl.enadeag.carchargeplanner.engine.forecast.SettingSpec;
import nl.enadeag.carchargeplanner.engine.route.Candidate;
import nl.enadeag.carchargeplanner.engine.route.Drive;
import nl.enadeag.carchargeplanner.engine.route.RouteError;
import nl.enadeag.carchargeplanner.engine.route.RouteService;
import nl.enadeag.carchargeplanner.engine.route.RouteServiceInfo;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.MissingNode;

/**
 * Geoapify (https://www.geoapify.com), with the owner's own API key: its Geocoding API's search, favouring places
 * near home, finds the places, and its Routing API, by car, the distance and travel time. Neither the server's own
 * words nor anything that was sent reach an error message, as both can hold the key, a location text or coordinates.
 */
public final class Geoapify implements RouteService {

    /** What Geoapify tells about itself. */
    static final RouteServiceInfo INFO = new RouteServiceInfo(
            CalendarSecrets.ROUTE_SERVICE,
            "Geoapify",
            "https://www.geoapify.com/terms-and-conditions/",
            "Powered by Geoapify · © OpenStreetMap contributors",
            "The location text of each calendar event without coordinates of its own, the place's coordinates, your "
                    + "home location (latitude and longitude) and your API key.",
            Duration.ofHours(1), // a 429 without a time to retry may be the free plan's 3000 credits a day
            List.of(new SettingSpec(CalendarSecrets.API_KEY, "API key", "The API key of your own free Geoapify "
                    + "account.", false, true, SettingFormat.LETTERS_AND_DIGITS)));

    private static final String SEARCH = "https://api.geoapify.com/v1/geocode/search";
    private static final String ROUTING = "https://api.geoapify.com/v1/routing";
    /** The most places one search gives; the closest of them is chosen. */
    static final int RESULTS = 5;
    /** Geoapify's own level below which a match is declined. */
    static final double DECLINE_CONFIDENCE = 0.2;
    /** Five places are a few kilobytes. */
    static final ByteSize MAX_SEARCH_BODY = ByteSize.ofKilobytes(256);
    /** A route comes with its line on the map, some ten thousand points for a long drive. */
    static final ByteSize MAX_ROUTE_BODY = HttpGet.DEFAULT_MAX_BODY;
    private static final long MAX_TOKENS = 1_000_000;
    private static final JsonMapper JSON = JsonMapper.builder(JsonFactory.builder()
            .streamReadConstraints(StreamReadConstraints.builder()
                    .maxDocumentLength(MAX_ROUTE_BODY.bytes())
                    .maxTokenCount(MAX_TOKENS)
                    .build())
            .build()).build();

    private final HttpGet http;

    /**
     * Makes the service.
     *
     * @param http the only way it reaches the network; tests pass recorded answers
     */
    public Geoapify(HttpGet http) {
        this.http = http;
    }

    @Override
    public RouteServiceInfo info() {
        return INFO;
    }

    @Override
    public List<Candidate> search(String text, Coordinates near, Map<String, String> settings) throws RouteError {
        var query = new LinkedHashMap<String, String>();
        query.put("text", text);
        query.put("bias", "proximity:" + degrees(near.longitude()) + "," + degrees(near.latitude()));
        query.put("limit", String.valueOf(RESULTS));
        query.put("format", "json");
        query.put("apiKey", key(settings));
        var reply = send(new HttpGet.Request(address(SEARCH, query), Map.of("Accept", "application/json"),
                HttpGet.DEFAULT_TIMEOUT, MAX_SEARCH_BODY));
        checkStatus(reply);
        var rows = json(reply.body()).path("results");
        if (!rows.isArray()) {
            throw new RouteError.BadResponse("Geoapify gave a search answer without results");
        }
        var places = new ArrayList<Candidate>();
        rows.forEach(row -> candidate(row, text).ifPresent(places::add));
        return places;
    }

    @Override
    public Optional<Drive> drive(Coordinates from, Coordinates to, Map<String, String> settings) throws RouteError {
        var query = new LinkedHashMap<String, String>();
        query.put("waypoints", point(from) + "|" + point(to));
        query.put("mode", "drive");
        query.put("units", "metric");
        query.put("format", "json");
        query.put("apiKey", key(settings));
        var reply = send(new HttpGet.Request(address(ROUTING, query), Map.of("Accept", "application/json"),
                HttpGet.DEFAULT_TIMEOUT, MAX_ROUTE_BODY));
        int status = reply.status();
        if (status >= 400 && status < 500 && status != 429 && !refusesTheKey(status)) {
            return Optional.empty(); // Geoapify answers a route it cannot make with a 4xx
        }
        checkStatus(reply);
        var body = json(reply.body());
        var routes = body.path("results");
        if (!routes.isArray()) {
            throw new RouteError.BadResponse("Geoapify gave a route answer without results");
        }
        if (routes.isEmpty()) {
            return Optional.empty();
        }
        var meters = routes.get(0).path("distance");
        var seconds = routes.get(0).path("time");
        boolean understood = meters.isNumber() && seconds.isNumber() && Double.isFinite(meters.doubleValue())
                && Double.isFinite(seconds.doubleValue()) && meters.doubleValue() >= 0 && seconds.doubleValue() >= 0;
        if (!understood) {
            throw new RouteError.BadResponse("Geoapify gave a route without a distance and a time");
        }
        return Optional.of(new Drive(meters.doubleValue() / 1000,
                Duration.ofMillis(Math.round(seconds.doubleValue() * 1000))));
    }

    /**
     * The API key, checked before it goes into an address.
     *
     * @throws RouteError.MissingSetting when there is none, or it holds more than letters and digits, such as a line
     *     break or an "&amp;" that would change what is asked
     */
    private static String key(Map<String, String> settings) throws RouteError.MissingSetting {
        var key = settings.getOrDefault(CalendarSecrets.API_KEY, "").strip();
        if (key.isEmpty()) {
            throw new RouteError.MissingSetting("the API key is required");
        }
        if (!SettingFormat.LETTERS_AND_DIGITS.accepts(key)) {
            throw new RouteError.MissingSetting("the API key may hold only letters and digits");
        }
        return key;
    }

    /** One place, or nothing when it lacks valid coordinates or Geoapify itself declines the match. */
    private static Optional<Candidate> candidate(JsonNode row, String text) {
        var latitude = row.path("lat");
        var longitude = row.path("lon");
        if (!latitude.isNumber() || !longitude.isNumber()
                || !Coordinates.areValid(latitude.doubleValue(), longitude.doubleValue())) {
            return Optional.empty();
        }
        var confidence = row.path("rank").path("confidence");
        if (confidence.isNumber() && confidence.doubleValue() < DECLINE_CONFIDENCE) {
            return Optional.empty();
        }
        var name = Optional.of(row.path("formatted")).filter(JsonNode::isString).map(JsonNode::stringValue)
                .filter(value -> !value.isBlank()).orElse(text);
        var location = new Coordinates(latitude.doubleValue(), longitude.doubleValue());
        return Optional.of(new Candidate(name.strip(), location));
    }

    private HttpGet.Reply send(HttpGet.Request request) throws RouteError.Unavailable {
        try {
            return http.get(request);
        } catch (HttpGet.TooLarge _) {
            throw new RouteError.Unavailable("Geoapify gave an answer larger than " + request.maxBody().bytes()
                    + " bytes");
        } catch (IOException e) {
            // the address holds the key, and an IOException's message may hold the address
            throw new RouteError.Unavailable("could not reach Geoapify (" + e.getClass().getSimpleName() + ")");
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            throw new RouteError.Unavailable("the request to Geoapify was interrupted");
        }
    }

    private static void checkStatus(HttpGet.Reply reply) throws RouteError {
        if (reply.ok()) {
            return;
        }
        if (reply.status() == 429) {
            throw new RouteError.RateLimited("Geoapify says too many requests", reply.retryAfter().orElse(null));
        }
        if (reply.status() >= 500) {
            throw new RouteError.Unavailable("Geoapify has a problem (status " + reply.status() + ")");
        }
        if (refusesTheKey(reply.status())) {
            throw new RouteError.Refused("Geoapify refused the request (status " + reply.status()
                    + "): check the API key");
        }
        throw new RouteError.BadResponse("Geoapify did not accept the request (status " + reply.status() + ")");
    }

    private static boolean refusesTheKey(int status) {
        return status == 401 || status == 403;
    }

    private static JsonNode json(String body) {
        try {
            return JSON.readTree(body);
        } catch (JacksonException _) {
            return MissingNode.getInstance();
        }
    }

    private static URI address(String server, Map<String, String> query) {
        return URI.create(server + "?" + query.entrySet().stream()
                .map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&")));
    }

    /** A waypoint as the Routing API takes it by default: latitude first. */
    private static String point(Coordinates place) {
        return degrees(place.latitude()) + "," + degrees(place.longitude());
    }

    /** Five decimals are about a metre. */
    private static String degrees(double value) {
        return BigDecimal.valueOf(value).setScale(5, RoundingMode.HALF_EVEN).stripTrailingZeros().toPlainString();
    }
}
