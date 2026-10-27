package nl.enadeag.carchargeplanner.engine.route;

import nl.enadeag.carchargeplanner.engine.config.Coordinates;
import nl.enadeag.carchargeplanner.engine.forecast.HttpGet;
import nl.enadeag.carchargeplanner.engine.forecast.ReplayHttp;
import nl.enadeag.carchargeplanner.engine.forecast.ReplayHttp.Route;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Describes a route service for the {@link RouteServiceConformance} kit: how to make it and the answers to play.
 *
 * @param make makes the service, given the HTTP client that replays the answers
 * @param ok the answers of a working service: a search that finds {@link #PLACE_TEXT} and a route to it
 * @param serverError an answer in which the server fails, which must count as unavailable
 * @param badBody an answer that is not as the service documents it, which must count as a bad response
 * @param emptyBody an empty answer, which must never crash the service
 * @param rateLimited the answer that says the service was asked too often
 * @param retryAfter what the rate-limited answer says about when to ask again, or null when it says nothing
 * @param refused the answer that refuses the API key
 * @param settings a working set of settings, the secret ones with recognisable values
 * @param errorSaying an error answer in which the service's server gives the text it is handed, as its own words
 */
public record RouteServiceCase(
        Function<HttpGet, RouteService> make,
        List<Route> ok,
        List<Route> serverError,
        List<Route> badBody,
        List<Route> emptyBody,
        List<Route> rateLimited,
        Duration retryAfter,
        List<Route> refused,
        Map<String, String> settings,
        Function<String, List<Route>> errorSaying) {

    /** The home in Zonnedorp, a made-up place. */
    public static final Coordinates HOME = new Coordinates(51.23456, 5.67891);

    /** A made-up address in Zeedorp, which the working answers find. */
    public static final String PLACE_TEXT = "Havenkade 7, Zeedorp";

    /** Made-up coordinates of a place, half a degree north of the home. */
    public static final Coordinates PLACE = new Coordinates(51.73456, 5.67891);

    public record Made(RouteService service, ReplayHttp http) {
    }

    public Made service(List<Route> routes) {
        var http = new ReplayHttp(routes);
        return new Made(make.apply(http), http);
    }
}
