package io.github.enadeag.carchargeplanner.engine.forecast.providers;

import io.github.enadeag.carchargeplanner.engine.config.PanelArray;
import io.github.enadeag.carchargeplanner.engine.forecast.ForecastStep;
import io.github.enadeag.carchargeplanner.engine.forecast.ProviderError;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import tools.jackson.databind.JsonNode;

/**
 * Reads Open-Meteo's hourly answer. The value stamped T is the mean of the hour before it, so it covers [T - 1 h, T).
 */
final class OpenMeteoSeries {

    /** How many days are asked for. */
    static final int FORECAST_DAYS = 4;
    private static final Duration HOUR = Duration.ofHours(1);

    private OpenMeteoSeries() {
    }

    /**
     * Turns one array's answer into steps.
     *
     * @param body the answer's body
     * @param array the panel array asked for, whose size and losses turn sunlight into power
     * @return an hourly step per value; they may break the contract (negative or out of order)
     * @throws ProviderError.BadResponse when the answer is not JSON, reports an error, or is not what the service
     *     documents
     */
    static List<ForecastStep> steps(String body, PanelArray array) throws ProviderError.BadResponse {
        var root = Exchange.json(body);
        if (root.isMissingNode()) {
            throw new ProviderError.BadResponse("Open-Meteo gave an answer that is not JSON");
        }
        if (!root.isObject()) {
            throw new ProviderError.BadResponse("Open-Meteo gave an unexpected answer");
        }
        if (root.path("error").asBoolean(false)) {
            throw new ProviderError.BadResponse("Open-Meteo reports an error: "
                    + reason(body).orElse("no reason given"));
        }
        var hourly = root.path("hourly");
        var stamps = hourly.path("time");
        var irradiance = hourly.path("global_tilted_irradiance");
        if (!stamps.isArray() || !irradiance.isArray() || stamps.size() != irradiance.size()) {
            throw notAsDocumented();
        }
        var steps = new ArrayList<ForecastStep>();
        for (int i = 0; i < stamps.size(); i++) {
            var end = end(stamps.get(i));
            steps.add(new ForecastStep(end.minus(HOUR), end, power(irradiance.get(i), array)));
        }
        return steps;
    }

    /**
     * The reason Open-Meteo gives in an error answer.
     *
     * @param body the answer's body, which may be anything
     * @return the reason, or empty when there is none
     */
    static Optional<String> reason(String body) {
        var reason = Exchange.json(body).path("reason");
        return reason.isString() && !reason.stringValue().isEmpty() ? Optional.of(reason.stringValue()) : Optional.empty();
    }

    private static Instant end(JsonNode stamp) throws ProviderError.BadResponse {
        if (!stamp.isIntegralNumber() || !stamp.canConvertToLong()) {
            throw notAsDocumented();
        }
        try {
            return Instant.ofEpochSecond(stamp.longValue());
        } catch (DateTimeException e) {
            throw notAsDocumented();
        }
    }

    /** A negative value is passed on, for the contract check to refuse. */
    private static double power(JsonNode watts, PanelArray array) throws ProviderError.BadResponse {
        if (!(watts.isNumber() || watts.isNull())) {
            throw notAsDocumented();
        }
        double wattsPerM2 = watts.isNull() ? 0 : watts.doubleValue(); // null at the edge of the forecast horizon
        return OpenMeteo.powerKw(wattsPerM2, array.kwp(), array.systemLosses());
    }

    private static ProviderError.BadResponse notAsDocumented() {
        return new ProviderError.BadResponse("Open-Meteo gave an answer that is not what it documents");
    }
}
