package io.github.enadeag.carchargeplanner.engine.forecast;

import io.github.enadeag.carchargeplanner.engine.config.HomeSystem;
import io.github.enadeag.carchargeplanner.engine.config.PanelArray;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Open-Meteo (https://open-meteo.com), free and without an API key. It forecasts the sunlight on a tilted plane, which
 * is turned into power. Each panel array is one request; the arrays' power is added up hour by hour.
 */
public final class OpenMeteo implements ForecastProvider {

    private static final String SERVER = "https://api.open-meteo.com/v1/forecast";
    private static final int FORECAST_DAYS = 4;
    private static final Duration HOUR = Duration.ofHours(1);

    private final HttpGet http;
    private final JsonMapper json = new JsonMapper();

    public OpenMeteo(HttpGet http) {
        this.http = http;
    }

    /** AC power of panels from the sunlight on them. */
    static double powerKw(double irradianceWm2, double kwp, double systemLosses) {
        return kwp * (irradianceWm2 / 1000) * (1 - systemLosses);
    }

    static URI requestUri(HomeSystem system, PanelArray array) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("latitude", system.latitude());
        query.put("longitude", system.longitude());
        query.put("hourly", "global_tilted_irradiance");
        query.put("tilt", array.tiltDeg());
        query.put("azimuth", array.azimuthDeg());
        query.put("timezone", system.timezone().getId());
        query.put("forecast_days", FORECAST_DAYS);
        query.put("timeformat", "unixtime");
        return URI.create(SERVER + "?" + query.entrySet().stream()
                .map(e -> e.getKey() + "=" + URLEncoder.encode(String.valueOf(e.getValue()), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&")));
    }

    @Override
    public List<ForecastStep> forecast(HomeSystem system) throws ForecastUnavailable {
        List<ForecastStep> total = null;
        for (var array : system.panels()) {
            var steps = forecastFor(system, array);
            total = total == null ? steps : addUp(total, steps);
        }
        return total;
    }

    private List<ForecastStep> forecastFor(HomeSystem system, PanelArray array) throws ForecastUnavailable {
        HttpGet.HttpReply reply;
        try {
            reply = http.get(requestUri(system, array));
        } catch (IOException e) {
            throw new ForecastUnavailable("Could not reach Open-Meteo (" + e.getClass().getSimpleName() + ")");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ForecastUnavailable("The request to Open-Meteo was interrupted");
        }
        if (reply.status() != 200) {
            throw new ForecastUnavailable("Open-Meteo did not answer the request (status " + reply.status() + ")");
        }
        return steps(reply.body(), array);
    }

    /** The value stamped T is the mean of the hour before it, so it covers [T - 1 h, T). */
    private List<ForecastStep> steps(String body, PanelArray array) throws ForecastUnavailable {
        JsonNode hourly;
        try {
            hourly = json.readTree(body).path("hourly");
        } catch (JacksonException e) {
            throw notAsDocumented();
        }
        var stamps = hourly.path("time");
        var irradiance = hourly.path("global_tilted_irradiance");
        if (!stamps.isArray() || !irradiance.isArray() || stamps.size() != irradiance.size() || stamps.isEmpty()) {
            throw notAsDocumented();
        }
        var steps = new ArrayList<ForecastStep>();
        for (int i = 0; i < stamps.size(); i++) {
            var stamp = stamps.get(i);
            var watts = irradiance.get(i);
            if (!stamp.isIntegralNumber() || !stamp.canConvertToLong() || !(watts.isNumber() || watts.isNull())) {
                throw notAsDocumented();
            }
            double wattsPerM2 = watts.isNull() ? 0 : watts.doubleValue(); // null at the edge of the forecast horizon
            if (wattsPerM2 < 0) {
                throw notAsDocumented();
            }
            Instant end;
            try {
                end = Instant.ofEpochSecond(stamp.longValue());
            } catch (DateTimeException e) {
                throw notAsDocumented();
            }
            var start = end.minus(HOUR);
            if (!steps.isEmpty() && !steps.getLast().end().equals(start)) {
                throw notAsDocumented();
            }
            steps.add(new ForecastStep(start, end, powerKw(wattsPerM2, array.kwp(), array.systemLosses())));
        }
        return steps;
    }

    private static List<ForecastStep> addUp(List<ForecastStep> a, List<ForecastStep> b) throws ForecastUnavailable {
        if (a.size() != b.size()) {
            throw notAsDocumented();
        }
        var sum = new ArrayList<ForecastStep>(a.size());
        for (int i = 0; i < a.size(); i++) {
            var x = a.get(i);
            var y = b.get(i);
            if (!x.start().equals(y.start())) {
                throw notAsDocumented();
            }
            sum.add(new ForecastStep(x.start(), x.end(), x.powerKw() + y.powerKw()));
        }
        return sum;
    }

    private static ForecastUnavailable notAsDocumented() {
        return new ForecastUnavailable("Open-Meteo gave an answer that is not what it documents");
    }
}
