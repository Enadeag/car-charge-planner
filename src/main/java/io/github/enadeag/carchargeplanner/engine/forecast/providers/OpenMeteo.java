package io.github.enadeag.carchargeplanner.engine.forecast.providers;

import io.github.enadeag.carchargeplanner.engine.config.PanelArray;
import io.github.enadeag.carchargeplanner.engine.config.ProviderId;
import io.github.enadeag.carchargeplanner.engine.forecast.ForecastProvider;
import io.github.enadeag.carchargeplanner.engine.forecast.ForecastStep;
import io.github.enadeag.carchargeplanner.engine.forecast.HttpGet;
import io.github.enadeag.carchargeplanner.engine.forecast.PlaceSearch;
import io.github.enadeag.carchargeplanner.engine.forecast.ProviderError;
import io.github.enadeag.carchargeplanner.engine.forecast.ProviderInfo;
import io.github.enadeag.carchargeplanner.engine.forecast.SettingSpec;
import io.github.enadeag.carchargeplanner.engine.forecast.Steps;
import io.github.enadeag.carchargeplanner.engine.forecast.SystemDescription;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Open-Meteo (https://open-meteo.com), free for non-commercial use. It forecasts the sunlight on a tilted plane, which
 * is turned into power, so each panel array is one request and the arrays' power is added up.
 */
public final class OpenMeteo implements ForecastProvider {

    /** What Open-Meteo tells about itself. */
    static final ProviderInfo INFO = new ProviderInfo(
            new ProviderId("open-meteo"),
            "Open-Meteo",
            "A free weather service run by Open-Meteo.com. It forecasts the sunlight on your panels and the app turns "
                    + "that into the power of your system. For non-commercial use.",
            "https://open-meteo.com/en/terms",
            "Weather data: Open-Meteo.com (CC BY 4.0)",
            "Your location (latitude and longitude), your time zone and the tilt and direction of your panels. "
                    + "With an API key, the key too.",
            Duration.ofMinutes(60),
            Duration.ofDays(OpenMeteoSeries.FORECAST_DAYS),
            Duration.ofHours(12),
            List.of(
                    new SettingSpec("base_url", "Server address", "Leave empty for the free public service. A paid "
                            + "plan, or a server of your own, has its own address.", true, false),
                    new SettingSpec("api_key", "API key", "Only for a paid plan (commercial use). Free use needs none.",
                            true, true)));

    private static final String PUBLIC_SERVER = "https://api.open-meteo.com";
    private static final String PATH = "/v1/forecast";

    private final HttpGet http;
    private final OpenMeteoPlaces places;

    /**
     * Makes the provider, with its place search.
     *
     * @param http the only way it reaches the network; tests pass recorded answers
     */
    public OpenMeteo(HttpGet http) {
        this.http = http;
        this.places = new OpenMeteoPlaces(http);
    }

    @Override
    public ProviderInfo info() {
        return INFO;
    }

    /** Its place search: the same family of services, under the same terms. */
    @Override
    public Optional<PlaceSearch> placeSearch() {
        return Optional.of(places);
    }

    /**
     * AC power of panels from the sunlight on them.
     *
     * @param irradianceWm2 the sunlight on the panels' plane, in W/m², 0 or more
     * @param kwp the panels' size in kWp
     * @param systemLosses the share lost in cables, inverter and dirt, from 0 up to 1
     * @return the power in kW
     */
    static double powerKw(double irradianceWm2, double kwp, double systemLosses) {
        return kwp * (irradianceWm2 / 1000) * (1 - systemLosses);
    }

    /**
     * The request for one panel array.
     *
     * @param system the place of the panels and its time zone
     * @param array the panel array to ask for
     * @param settings the provider's settings: "base_url" (blank for the public service) and "api_key" (blank for
     *     none)
     * @return the request's address
     * @throws ProviderError.NotConfigured when the server address is not https (or plain http on this computer) or
     *     not a web address
     */
    static URI requestUri(SystemDescription system, PanelArray array, Map<String, String> settings)
            throws ProviderError.NotConfigured {
        var server = server(settings);
        Map<String, String> query = new LinkedHashMap<>();
        query.put("latitude", Decimals.plain(system.latitude()));
        query.put("longitude", Decimals.plain(system.longitude()));
        query.put("hourly", "global_tilted_irradiance");
        query.put("tilt", Decimals.plain(array.tiltDeg()));
        query.put("azimuth", Decimals.plain(array.azimuthDeg()));
        query.put("timezone", system.timezone().getId());
        query.put("forecast_days", String.valueOf(OpenMeteoSeries.FORECAST_DAYS));
        query.put("timeformat", "unixtime");
        var key = settings.getOrDefault("api_key", "").strip();
        if (!key.isEmpty()) {
            query.put("apikey", key); // the way Open-Meteo documents it for its customer servers
        }
        try {
            return URI.create(server + PATH + "?" + query.entrySet().stream()
                    .map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                    .collect(Collectors.joining("&")));
        } catch (IllegalArgumentException e) {
            throw new ProviderError.NotConfigured("the server address is not a web address");
        }
    }

    /** A server of your own on this computer may use plain http; anywhere else the key would travel readable. */
    private static String server(Map<String, String> settings) throws ProviderError.NotConfigured {
        var server = settings.getOrDefault("base_url", "").strip().replaceAll("/+$", "");
        if (server.isEmpty()) {
            return PUBLIC_SERVER;
        }
        if (!(server.startsWith("https://") || server.startsWith("http://localhost")
                || server.startsWith("http://127.0.0.1"))) {
            throw new ProviderError.NotConfigured("the server address must start with https://");
        }
        return server;
    }

    @Override
    public List<ForecastStep> fetch(SystemDescription system, Map<String, String> settings, Instant now)
            throws ProviderError {
        for (var array : system.arrays()) {
            requestUri(system, array, settings); // a wrong address is reported before anything is sent
        }
        return Steps.requireValid(Steps.eachArray(system, array -> fetchArray(system, array, settings)));
    }

    private List<ForecastStep> fetchArray(SystemDescription system, PanelArray array, Map<String, String> settings)
            throws ProviderError {
        var reply = Exchange.send(http, HttpGet.Request.of(requestUri(system, array, settings)), INFO.name());
        checkStatus(reply);
        return Steps.requireValid(OpenMeteoSeries.steps(reply.body(), array));
    }

    private static void checkStatus(HttpGet.Reply reply) throws ProviderError {
        if (reply.ok()) {
            return;
        }
        if (reply.status() == 429) {
            throw new ProviderError.RateLimited("Open-Meteo says too many requests", reply.retryAfter().orElse(null));
        }
        if (reply.status() >= 500) {
            throw new ProviderError.Unavailable("Open-Meteo has a problem (status " + reply.status() + ")");
        }
        if (reply.status() == 401 || reply.status() == 403) {
            throw new ProviderError.BadResponse("Open-Meteo refused the request (status " + reply.status()
                    + "): check the API key and server address");
        }
        throw new ProviderError.BadResponse("Open-Meteo did not accept the request (status " + reply.status() + ")"
                + OpenMeteoSeries.reason(reply.body()).map(": "::concat).orElse(""));
    }
}
