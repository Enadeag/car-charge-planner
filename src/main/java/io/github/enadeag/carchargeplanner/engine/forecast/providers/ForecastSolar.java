package io.github.enadeag.carchargeplanner.engine.forecast.providers;

import io.github.enadeag.carchargeplanner.engine.config.PanelArray;
import io.github.enadeag.carchargeplanner.engine.forecast.ForecastProvider;
import io.github.enadeag.carchargeplanner.engine.forecast.ForecastStep;
import io.github.enadeag.carchargeplanner.engine.forecast.HttpGet;
import io.github.enadeag.carchargeplanner.engine.forecast.ProviderError;
import io.github.enadeag.carchargeplanner.engine.forecast.ProviderInfo;
import io.github.enadeag.carchargeplanner.engine.forecast.SettingSpec;
import io.github.enadeag.carchargeplanner.engine.forecast.Steps;
import io.github.enadeag.carchargeplanner.engine.forecast.SystemDescription;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Forecast.Solar (https://forecast.solar); the free public plan needs no key. The public plan forecasts one plane per
 * request, and a paid plan's limit on planes cannot be seen from the key, so each panel array is asked for separately
 * and the answers are added up; a rate limit on any request is one for the whole forecast.
 */
public final class ForecastSolar implements ForecastProvider {

    static final ProviderInfo INFO = new ProviderInfo(
            "forecast-solar",
            "Forecast.Solar",
            "A solar forecast service (Forecast.Solar) that estimates the output of your system itself, with its own "
                    + "allowance for losses, so the system losses setting is not used. The free public plan gives "
                    + "today and tomorrow, hour by hour, and allows 12 requests an hour for everyone behind your "
                    + "internet address. Each panel array takes one request.",
            "https://doc.forecast.solar/account_models",
            "Solar forecast: Forecast.Solar",
            "Your location (latitude and longitude), the tilt and direction of your panels and their size in kWp. "
                    + "With an API key, the key too.",
            Duration.ofMinutes(60), // the 12 calls an hour are shared by everything behind the address, one per array
            Duration.ofDays(2),
            Duration.ofHours(6),
            List.of(new SettingSpec("api_key", "API key", "Only for a paid plan, which adds more days and finer "
                    + "steps. The free public plan needs none.", true, true)));

    private static final String SERVER = "https://api.forecast.solar";
    private static final String ROUTE = "estimate/watthours/period";

    private final HttpGet http;

    /**
     * Makes the provider.
     *
     * @param http the only way it reaches the network; tests pass recorded answers
     */
    public ForecastSolar(HttpGet http) {
        this.http = http;
    }

    @Override
    public ProviderInfo info() {
        return INFO;
    }

    /**
     * The request for one panel array, with the key, when there is one, in front of the route. Tilt and direction are
     * whole degrees in this API, and its azimuth is ours: 0 south, -90 east, 90 west.
     */
    static URI requestUri(SystemDescription system, PanelArray array, Map<String, String> settings) {
        var parts = new ArrayList<String>();
        parts.add(SERVER);
        var key = settings.getOrDefault("api_key", "").strip();
        if (!key.isEmpty()) {
            parts.add(key);
        }
        parts.add(ROUTE);
        parts.add(Decimals.rounded(system.latitude(), 4));
        parts.add(Decimals.rounded(system.longitude(), 4));
        parts.add(wholeDegrees(array.tiltDeg(), 0, 90));
        parts.add(wholeDegrees(array.azimuthDeg(), -180, 180));
        parts.add(Decimals.rounded(array.kwp(), 3));
        return URI.create(String.join("/", parts));
    }

    /** Rounded half to even, as the Python version did, so both ask for the same plane. */
    private static String wholeDegrees(double degrees, int lowest, int highest) {
        return String.valueOf((long) Math.min(highest, Math.max(lowest, Math.rint(degrees))));
    }

    @Override
    public List<ForecastStep> fetch(SystemDescription system, Map<String, String> settings, Instant now)
            throws ProviderError {
        return Steps.requireValid(Steps.eachArray(system, array -> fetchArray(system, array, settings, now)));
    }

    private List<ForecastStep> fetchArray(SystemDescription system, PanelArray array, Map<String, String> settings,
            Instant now) throws ProviderError {
        var request = new HttpGet.Request(requestUri(system, array, settings), Map.of("Accept", "application/json"),
                HttpGet.DEFAULT_TIMEOUT);
        var reply = Exchange.send(http, request, INFO.name());
        checkStatus(reply, now);
        return Steps.requireValid(ForecastSolarPeriods.steps(reply.body(), system.timezone()));
    }

    private static void checkStatus(HttpGet.Reply reply, Instant now) throws ProviderError {
        if (reply.ok()) {
            return;
        }
        if (reply.status() == 429) {
            throw new ProviderError.RateLimited("Forecast.Solar says too many requests",
                    ForecastSolarPeriods.retryAfter(reply, now).orElse(null));
        }
        if (reply.status() >= 500) {
            throw new ProviderError.Unavailable("Forecast.Solar has a problem (status " + reply.status() + ")");
        }
        if (reply.status() == 401 || reply.status() == 403) {
            throw new ProviderError.BadResponse("Forecast.Solar refused the request (status " + reply.status()
                    + "): check the API key");
        }
        throw new ProviderError.BadResponse("Forecast.Solar did not accept the request (status " + reply.status() + ")"
                + ForecastSolarPeriods.messageText(reply.body()).map(": "::concat).orElse(""));
    }
}
