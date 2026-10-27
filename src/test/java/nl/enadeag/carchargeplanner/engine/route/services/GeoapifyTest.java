package nl.enadeag.carchargeplanner.engine.route.services;

import static nl.enadeag.carchargeplanner.engine.forecast.ReplayHttp.Route.answer;
import static nl.enadeag.carchargeplanner.engine.route.RouteServiceCase.HOME;
import static nl.enadeag.carchargeplanner.engine.route.RouteServiceCase.PLACE;
import static nl.enadeag.carchargeplanner.engine.route.RouteServiceCase.PLACE_TEXT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.tuple;

import nl.enadeag.carchargeplanner.engine.calendar.CalendarSecrets;
import nl.enadeag.carchargeplanner.engine.config.Coordinates;
import nl.enadeag.carchargeplanner.engine.forecast.HttpGet.Reply;
import nl.enadeag.carchargeplanner.engine.forecast.ReplayHttp;
import nl.enadeag.carchargeplanner.engine.route.Candidate;
import nl.enadeag.carchargeplanner.engine.route.Drive;
import nl.enadeag.carchargeplanner.engine.route.RecordedRoutes;
import nl.enadeag.carchargeplanner.engine.route.RouteError;
import nl.enadeag.carchargeplanner.engine.route.RouteServiceCase;
import nl.enadeag.carchargeplanner.engine.route.RouteServiceConformance;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

class GeoapifyTest extends RouteServiceConformance {

    private static final String SECRET = "GeoSecretKey77";
    private static final Map<String, String> SETTINGS = Map.of(CalendarSecrets.API_KEY, SECRET);
    private static final String SEARCH_ONE = RecordedRoutes.answer("geoapify_search_one.json");
    private static final String SEARCH_SEVERAL = RecordedRoutes.answer("geoapify_search_several.json");
    private static final String ROUTE = RecordedRoutes.answer("geoapify_route.json");

    @Override
    protected RouteServiceCase serviceCase() {
        return new RouteServiceCase(Geoapify::new,
                List.of(answer("/geocode/search", new Reply(200, SEARCH_ONE)),
                        answer("/routing", new Reply(200, ROUTE))),
                List.of(answer("", new Reply(502, "Bad Gateway"))),
                List.of(answer("", new Reply(200, "<html>maintenance</html>"))),
                List.of(answer("", new Reply(200, ""))),
                List.of(answer("", new Reply(429, RecordedRoutes.answer("geoapify_429.json"),
                        Map.of("Retry-After", "120")))),
                Duration.ofSeconds(120),
                List.of(answer("", new Reply(401, RecordedRoutes.answer("geoapify_401.json")))),
                SETTINGS,
                text -> List.of(answer("", new Reply(400, error(text)))));
    }

    private static String error(String text) {
        return new JsonMapper().createObjectNode().put("statusCode", 400).put("error", "Bad Request")
                .put("message", text).toString();
    }

    private static List<Candidate> search(String body) throws RouteError {
        return new Geoapify(ReplayHttp.always(new Reply(200, body))).search(PLACE_TEXT, HOME, SETTINGS);
    }

    @Test
    void theSearchSendsTheTextFavouringPlacesNearHomeLongitudeFirst() throws RouteError {
        var http = ReplayHttp.always(new Reply(200, SEARCH_ONE));
        new Geoapify(http).search(PLACE_TEXT, HOME, SETTINGS);
        assertThat(http.requests.getFirst().uri()).hasToString("https://api.geoapify.com/v1/geocode/search"
                + "?text=Havenkade+7%2C+Zeedorp&bias=proximity%3A5.67891%2C51.23456&limit=5&format=json"
                + "&apiKey=GeoSecretKey77");
    }

    @Test
    void theRouteIsAskedByCarFromHomeLatitudeFirst() throws RouteError {
        var http = ReplayHttp.always(new Reply(200, ROUTE));
        new Geoapify(http).drive(HOME, new Coordinates(51.7345649, -0.1), SETTINGS);
        assertThat(http.requests.getFirst().uri()).hasToString("https://api.geoapify.com/v1/routing"
                + "?waypoints=51.23456%2C5.67891%7C51.73456%2C-0.1&mode=drive&units=metric&format=json"
                + "&apiKey=GeoSecretKey77");
    }

    @Test
    void theRouteGivesItsDistanceInKmAndItsTime() throws RouteError {
        var drive = new Geoapify(ReplayHttp.always(new Reply(200, ROUTE))).drive(HOME, PLACE, SETTINGS);
        assertThat(drive).contains(new Drive(63.25, Duration.ofMillis(2_712_400)));
    }

    @Test
    void eachPlaceFoundKeepsItsAddressAndCoordinatesInTheServicesOrder() throws RouteError {
        assertThat(search(SEARCH_SEVERAL)).extracting(Candidate::name, Candidate::location).containsExactly(
                tuple("Zeedorp, Far Province, Nowhere", new Coordinates(53.2, 6.4)),
                tuple("Havenkade 7, Zeedorp, Near Province, Nowhere",
                        new Coordinates(51.3, 5.7)),
                tuple("Zeedorp, Middle Province, Nowhere", new Coordinates(52.0, 5.5)));
    }

    @Test
    void nothingFoundIsNoPlace() throws RouteError {
        assertThat(search(RecordedRoutes.answer("geoapify_search_none.json"))).isEmpty();
    }

    /** Geoapify's own guidance declines a match below a confidence of 0.2; one without coordinates is no place. */
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "{\"lat\": 51.3, \"lon\": 5.7, \"formatted\": \"Zeedorp\", \"rank\": {\"confidence\": 0.19}} | 0",
            "{\"lat\": 51.3, \"lon\": 5.7, \"formatted\": \"Zeedorp\", \"rank\": {\"confidence\": 0.2}}  | 1",
            "{\"lat\": 51.3, \"lon\": 5.7, \"formatted\": \"Zeedorp\"}                                   | 1",
            "{\"lat\": 91, \"lon\": 5.7, \"formatted\": \"Zeedorp\"}                                     | 0",
            "{\"lon\": 5.7, \"formatted\": \"Zeedorp\"}                                                  | 0"})
    void aDeclinedMatchOrOneWithoutCoordinatesIsLeftOut(String result, int kept) throws RouteError {
        assertThat(search("{\"results\": [" + result + "]}")).hasSize(kept);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", ", \"formatted\": \"  \"", ", \"formatted\": 7"})
    void aPlaceWithoutAnAddressIsNamedByTheTextSearchedFor(String formatted) throws RouteError {
        assertThat(search("{\"results\": [{\"lat\": 51.3, \"lon\": 5.7" + formatted + "}]}"))
                .containsExactly(new Candidate(PLACE_TEXT, new Coordinates(51.3, 5.7)));
    }

    @Test
    void aRouteAtHomeIsNoDistanceInNoTime() throws RouteError {
        var body = "{\"results\": [{\"distance\": 0, \"time\": 0}]}";
        assertThat(new Geoapify(ReplayHttp.always(new Reply(200, body))).drive(HOME, HOME, SETTINGS))
                .contains(new Drive(0, Duration.ZERO));
    }

    @ParameterizedTest
    @CsvSource({"500, true", "499, false"})
    void aRouteStatusFrom500OnIsTheServersProblemAndBelowItNoRoute(int status, boolean serverProblem) {
        var geoapify = new Geoapify(ReplayHttp.always(new Reply(status, "")));
        var thrown = catchThrowable(() -> assertThat(geoapify.drive(HOME, PLACE, SETTINGS)).isEmpty());
        if (serverProblem) {
            assertThat(thrown).isInstanceOf(RouteError.Unavailable.class);
        } else {
            assertThat(thrown).isNull();
        }
    }

    @Test
    void anInterruptedRequestIsUnavailableAndKeepsTheThreadInterrupted() {
        var geoapify = new Geoapify(_ -> {
            throw new InterruptedException();
        });
        try {
            assertThatThrownBy(() -> geoapify.search(PLACE_TEXT, HOME, SETTINGS))
                    .isInstanceOf(RouteError.Unavailable.class);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {"400 | geoapify_route_none.json", "200 | ''"})
    void aRouteItCannotMakeIsNoRoute(int status, String file) throws RouteError {
        var body = file.isEmpty() ? "{\"results\": [], \"properties\": {\"mode\": \"drive\"}}"
                : RecordedRoutes.answer(file);
        assertThat(new Geoapify(ReplayHttp.always(new Reply(status, body))).drive(HOME, PLACE, SETTINGS)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"results\": [{\"time\": 60}]}",
            "{\"results\": [{\"distance\": 1000}]}",
            "{\"results\": [{\"distance\": -1, \"time\": 60}]}",
            "{\"results\": [{\"distance\": 1000, \"time\": -1}]}",
            "{\"results\": [{\"distance\": \"1000\", \"time\": 60}]}",
            "{\"results\": {\"distance\": 1000, \"time\": 60}}"})
    void aRouteWithoutAUsableDistanceAndTimeIsABadResponse(String body) {
        var geoapify = new Geoapify(ReplayHttp.always(new Reply(200, body)));
        assertThatThrownBy(() -> geoapify.drive(HOME, PLACE, SETTINGS)).isInstanceOf(RouteError.BadResponse.class);
    }
}
