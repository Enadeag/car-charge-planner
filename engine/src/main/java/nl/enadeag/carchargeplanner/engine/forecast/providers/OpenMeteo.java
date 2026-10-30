package nl.enadeag.carchargeplanner.engine.forecast.providers;

import nl.enadeag.carchargeplanner.engine.config.PanelArray;
import nl.enadeag.carchargeplanner.engine.config.ProviderId;
import nl.enadeag.carchargeplanner.engine.forecast.ArraySum;
import nl.enadeag.carchargeplanner.engine.forecast.ForecastProvider;
import nl.enadeag.carchargeplanner.engine.forecast.ForecastStep;
import nl.enadeag.carchargeplanner.engine.forecast.HttpGet;
import nl.enadeag.carchargeplanner.engine.forecast.PlaceSearch;
import nl.enadeag.carchargeplanner.engine.forecast.ProviderError;
import nl.enadeag.carchargeplanner.engine.forecast.ProviderInfo;
import nl.enadeag.carchargeplanner.engine.forecast.ProviderText;
import nl.enadeag.carchargeplanner.engine.forecast.ServerAddress;
import nl.enadeag.carchargeplanner.engine.forecast.SettingFormat;
import nl.enadeag.carchargeplanner.engine.forecast.SettingSpec;
import nl.enadeag.carchargeplanner.engine.forecast.SolarSite;
import nl.enadeag.carchargeplanner.engine.forecast.StepRules;
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
 * Open-Meteo (https://open-meteo.com), free for non-commercial use. It forecasts the sunlight on a tilted surface,
 * which is turned into power, so each panel array is one request and the arrays' power is added up.
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
                            + "plan, or a server of your own, has its own address.", true, false,
                            SettingFormat.SERVER_ADDRESS),
                    new SettingSpec("api_key", "API key", "Only for a paid plan (commercial use). Free use needs none.",
                            true, true)));

    private static final ServerAddress PUBLIC_SERVER = new ServerAddress(URI.create("https://api.open-meteo.com"));
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
     * @param irradianceWm2 the sunlight on the panels' surface, in W/m², 0 or more
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
     * @throws ProviderError.MissingSetting when the server address is not one {@link ServerAddress#parse} accepts
     */
    static URI requestUri(SolarSite system, PanelArray array, Map<String, String> settings)
            throws ProviderError.MissingSetting {
        var server = server(settings);
        Map<String, String> query = new LinkedHashMap<>();
        query.put("latitude", Decimals.plain(system.location().latitude()));
        query.put("longitude", Decimals.plain(system.location().longitude()));
        query.put("hourly", "global_tilted_irradiance");
        query.put("tilt", Decimals.plain(array.tiltDeg()));
        query.put("azimuth", Decimals.plain(array.azimuthDeg()));
        query.put("timezone", system.timezone().getId());
        query.put("forecast_days", String.valueOf(OpenMeteoSeries.FORECAST_DAYS));
        query.put("timeformat", "unixtime");
        var key = settings.getOrDefault("api_key", "").strip();
        if (!SettingFormat.TEXT.accepts(key)) {
            throw new ProviderError.MissingSetting(
                    "the API key must not hold a line break or another control character");
        }
        if (!key.isEmpty()) {
            query.put("apikey", key); // the way Open-Meteo documents it for its customer servers
        }
        try {
            return URI.create(server.withPath(PATH) + "?" + query.entrySet().stream()
                    .map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                    .collect(Collectors.joining("&")));
        } catch (IllegalArgumentException _) {
            throw new ProviderError.MissingSetting("the server address is not a web address");
        }
    }

    private static ServerAddress server(Map<String, String> settings) throws ProviderError.MissingSetting {
        var server = settings.getOrDefault("base_url", "");
        if (server.isBlank()) {
            return PUBLIC_SERVER;
        }
        return ServerAddress.parse(server).orElseThrow(() -> new ProviderError.MissingSetting("the server address must "
                + "start with https://, or http:// for localhost, 127.0.0.1 or [::1], with no user name, \"?\" or \"#\""));
    }

    @Override
    public List<ForecastStep> fetch(SolarSite system, Map<String, String> settings, Instant now)
            throws ProviderError {
        for (var array : system.arrays()) {
            requestUri(system, array, settings); // a wrong address is reported before anything is sent
        }
        return StepRules.requireValid(ArraySum.sumOverArrays(system, array -> fetchArray(system, array, settings)));
    }

    private List<ForecastStep> fetchArray(SolarSite system, PanelArray array, Map<String, String> settings)
            throws ProviderError {
        var reply = Exchange.send(http, HttpGet.Request.of(requestUri(system, array, settings)), INFO.name());
        var secrets = INFO.secretValues(settings);
        checkStatus(reply, secrets);
        return StepRules.requireValid(OpenMeteoSeries.steps(reply.body(), array, secrets));
    }

    private static void checkStatus(HttpGet.Reply reply, List<String> secrets) throws ProviderError {
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
                + OpenMeteoSeries.reason(reply.body())
                        .map(reason -> "; " + ProviderText.quoted(INFO.name(), reason, secrets)).orElse(""));
    }
}
