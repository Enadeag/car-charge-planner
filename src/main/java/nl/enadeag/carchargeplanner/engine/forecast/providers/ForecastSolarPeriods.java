package nl.enadeag.carchargeplanner.engine.forecast.providers;

import nl.enadeag.carchargeplanner.engine.forecast.ForecastStep;
import nl.enadeag.carchargeplanner.engine.forecast.HttpGet;
import nl.enadeag.carchargeplanner.engine.forecast.ProviderError;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import tools.jackson.databind.JsonNode;

/**
 * Reads Forecast.Solar's {@code estimate/watthours/period} answer. Each key is a local time at the place, and each
 * value the Wh of the period from the key before it on the same day; a day's first key (sunrise) gives no step.
 */
final class ForecastSolarPeriods {

    private static final DateTimeFormatter KEY = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss", Locale.ROOT);

    /** One key of the answer. */
    private record Point(Instant at, LocalDate day, double wattHours) {
    }

    private ForecastSolarPeriods() {
    }

    /**
     * Turns the answer into steps.
     *
     * @param body the answer's body
     * @param systemZone the time zone to read the keys in when the answer names none
     * @return a step for each period after a day's first key; they may break the contract (negative energy)
     * @throws ProviderError.BadResponse when the answer is not JSON, reports a problem, is empty, or is not what
     *     the service documents
     */
    static List<ForecastStep> steps(String body, ZoneId systemZone) throws ProviderError.BadResponse {
        var root = Exchange.json(body);
        if (root.isMissingNode()) {
            throw new ProviderError.BadResponse("Forecast.Solar gave an answer that is not JSON");
        }
        var message = root.path("message");
        if (!message.isObject()) {
            throw notAsDocumented();
        }
        if (message.path("code").asInt(0) != 0) {
            throw new ProviderError.BadResponse("Forecast.Solar reports a problem: " + text(message, "text")
                    .or(() -> text(message, "type")).orElse("no reason given"));
        }
        var result = root.path("result");
        if (!result.isObject() || result.isEmpty()) {
            throw new ProviderError.BadResponse("Forecast.Solar gave no forecast");
        }
        var placeZone = placeZone(message, systemZone);
        return stepsBetween(points(result, placeZone));
    }

    /** The keys are in the time zone of the place, which the answer names. */
    private static ZoneId placeZone(JsonNode message, ZoneId systemZone) throws ProviderError.BadResponse {
        var name = text(message.path("info"), "timezone");
        try {
            return name.isPresent() ? ZoneId.of(name.get()) : systemZone;
        } catch (DateTimeException e) {
            throw notAsDocumented();
        }
    }

    private static List<Point> points(JsonNode result, ZoneId placeZone) throws ProviderError.BadResponse {
        var points = new ArrayList<Point>();
        for (Map.Entry<String, JsonNode> entry : result.properties()) {
            LocalDateTime local;
            try {
                local = LocalDateTime.parse(entry.getKey(), KEY);
            } catch (DateTimeException e) {
                throw notAsDocumented();
            }
            // a time in the repeated autumn hour could be either of two instants; atZone takes the earlier one
            var at = local.atZone(placeZone).toInstant();
            points.add(new Point(at, local.toLocalDate(), wattHours(entry.getValue())));
        }
        points.sort(Comparator.comparing(Point::at));
        return points;
    }

    private static double wattHours(JsonNode value) throws ProviderError.BadResponse {
        if (!(value.isNumber() || value.isNull()) || !Double.isFinite(value.asDouble(0))) {
            throw notAsDocumented();
        }
        return value.isNull() ? 0 : value.doubleValue();
    }

    /** Power is energy over the real time elapsed, which across a daylight saving change is not the clock's. */
    private static List<ForecastStep> stepsBetween(List<Point> points) {
        var steps = new ArrayList<ForecastStep>();
        for (int i = 1; i < points.size(); i++) {
            var previous = points.get(i - 1);
            var point = points.get(i);
            if (previous.day().equals(point.day()) && point.at().isAfter(previous.at())) {
                double hours = Duration.between(previous.at(), point.at()).toMillis() / 3_600_000.0;
                steps.add(new ForecastStep(previous.at(), point.at(), point.wattHours() / 1000 / hours));
            }
        }
        return steps;
    }

    /**
     * When it may be asked again after a rate limit: the header, else the body.
     *
     * @param reply the refusal
     * @param now the moment of the refusal
     * @return the wait from now, zero when the time has passed; empty when neither says a time with a time zone
     */
    static Optional<Duration> retryAfter(HttpGet.Reply reply, Instant now) {
        var stamp = reply.header("X-Ratelimit-Retry-At")
                .or(() -> text(Exchange.json(reply.body()).path("message").path("ratelimit"), "retry-at"));
        try {
            var retryAt = OffsetDateTime.parse(stamp.orElse("")).toInstant();
            return Optional.of(retryAt.isAfter(now) ? Duration.between(now, retryAt) : Duration.ZERO);
        } catch (DateTimeException e) {
            return Optional.empty();
        }
    }

    /**
     * The reason the server gives in its answer's message.
     *
     * @param body the answer's body, which may be anything
     * @return the message's text, or empty when there is none
     */
    static Optional<String> messageText(String body) {
        return text(Exchange.json(body).path("message"), "text");
    }

    private static Optional<String> text(JsonNode node, String field) {
        var value = node.path(field);
        return value.isString() && !value.stringValue().isEmpty() ? Optional.of(value.stringValue()) : Optional.empty();
    }

    private static ProviderError.BadResponse notAsDocumented() {
        return new ProviderError.BadResponse("Forecast.Solar gave an answer that is not what it documents");
    }
}
