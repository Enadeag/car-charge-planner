package nl.enadeag.carchargeplanner.engine.route;

import static nl.enadeag.carchargeplanner.engine.forecast.ReplayHttp.Route.answer;
import static nl.enadeag.carchargeplanner.engine.route.RouteServiceCase.HOME;
import static nl.enadeag.carchargeplanner.engine.route.RouteServiceCase.PLACE;
import static nl.enadeag.carchargeplanner.engine.route.RouteServiceCase.PLACE_TEXT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import nl.enadeag.carchargeplanner.LogCapture;
import nl.enadeag.carchargeplanner.engine.calendar.CalendarSecrets;
import nl.enadeag.carchargeplanner.engine.config.Coordinates;
import nl.enadeag.carchargeplanner.engine.forecast.HttpGet;
import nl.enadeag.carchargeplanner.engine.forecast.HttpGet.Reply;
import nl.enadeag.carchargeplanner.engine.forecast.ReplayHttp;
import nl.enadeag.carchargeplanner.engine.forecast.ReplayHttp.Route;
import nl.enadeag.carchargeplanner.engine.route.RouteOutcome.Match;
import nl.enadeag.carchargeplanner.engine.route.RouteOutcome.Measure;
import nl.enadeag.carchargeplanner.engine.route.services.Geoapify;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class RouteLookupTest {

    private static final Instant NOW = Instant.parse("2026-10-27T08:00:00Z");
    private static final Map<String, String> SETTINGS = Map.of(CalendarSecrets.API_KEY, "GeoSecretKey77");
    private static final Route ONE_PLACE = answer("/geocode/search", recorded(200, "geoapify_search_one.json"));
    private static final Route A_ROUTE = answer("/routing", recorded(200, "geoapify_route.json"));
    private static final Route NO_ROUTE = answer("/routing", recorded(400, "geoapify_route_none.json"));
    private static final Drive THE_ROUTE = new Drive(63.25, Duration.ofMillis(2_712_400));

    /** Keeps the failures logged out of the test output. */
    private final LogCapture log = LogCapture.of(RouteLookup.class);

    @AfterEach
    void closeLog() {
        log.close();
    }

    /** Gives its answers in turn, one per request. */
    private static final class InTurn implements HttpGet {

        final List<Request> requests = new ArrayList<>();
        private final Deque<Reply> answers;

        InTurn(Reply... answers) {
            this.answers = new ArrayDeque<>(List.of(answers));
        }

        @Override
        public Reply get(Request request) {
            requests.add(request);
            return answers.removeFirst();
        }
    }

    private static Reply recorded(int status, String file) {
        return new Reply(status, RecordedRoutes.answer(file));
    }

    private static ReplayHttp replay(Route... routes) {
        return new ReplayHttp(List.of(routes));
    }

    private static RouteOutcome find(ReplayHttp http, Destination destination) {
        return new RouteLookup(new Geoapify(http)).find(destination, HOME, SETTINGS, NOW);
    }

    @Test
    void onePlaceFoundIsThePlaceWithItsRoute() {
        var http = replay(ONE_PLACE, A_ROUTE);
        assertThat(find(http, Destination.of(PLACE_TEXT)))
                .isEqualTo(new RouteOutcome.Found("Zeedorp, Nowhere", THE_ROUTE, Match.ONLY, Measure.ROUTE));
        assertThat(http.requests.getLast().uri().getRawQuery())
                .contains("waypoints=51.23456%2C5.67891%7C51.73456%2C5.67891");
    }

    @Test
    void ofSeveralPlacesTheOneClosestToHomeIsChosen() {
        var http = replay(answer("/geocode/search", recorded(200, "geoapify_search_several.json")), A_ROUTE);
        assertThat(find(http, Destination.of("Zeedorp"))).isEqualTo(new RouteOutcome.Found(
                "Havenkade 7, Zeedorp, Near Province, Nowhere", THE_ROUTE, Match.CHOSEN, Measure.ROUTE));
        assertThat(http.requests.getLast().uri().getRawQuery()).contains("%7C51.3%2C5.7&");
    }

    @Test
    void aPlaceNotFoundIsNoPlaceAndNoRouteIsAskedFor() {
        var http = replay(answer("/geocode/search", recorded(200, "geoapify_search_none.json")));
        assertThat(find(http, Destination.of("Nergenshuizen 99"))).isEqualTo(new RouteOutcome.NotFound());
        assertThat(http.requests).hasSize(1);
    }

    /** Half a degree of latitude is 55.597 km in a straight line: 72.277 km by road at 60 km/h is 4337 seconds. */
    @Test
    void withoutARouteTheStraightLineTimesOnePointThreeIsAnEstimate() {
        var outcome = find(replay(ONE_PLACE, NO_ROUTE), Destination.of(PLACE_TEXT));
        assertThat(outcome).isInstanceOfSatisfying(RouteOutcome.Found.class, found -> {
            assertThat(found.measure()).isEqualTo(Measure.ESTIMATE);
            assertThat(found.match()).isEqualTo(Match.ONLY);
            assertThat(found.drive().distanceKm()).isCloseTo(72.2767, within(1e-4));
            assertThat(found.drive().travelTime()).isEqualTo(Duration.ofSeconds(4337));
        });
    }

    /** Half a degree of longitude at the home's latitude is 34.811 km: 45.255 km by road, 2715 seconds. */
    @Test
    void anEstimateEastOfHomeCountsTheNarrowerDegreesOfLongitude() {
        var east = new Coordinates(HOME.latitude(), HOME.longitude() + 0.5);
        var outcome = find(replay(NO_ROUTE), Destination.at(PLACE_TEXT, east));
        assertThat(outcome).isInstanceOfSatisfying(RouteOutcome.Found.class, found -> {
            assertThat(found.drive().distanceKm()).isCloseTo(45.2548, within(1e-4));
            assertThat(found.drive().travelTime()).isEqualTo(Duration.ofSeconds(2715));
        });
    }

    @Test
    void coordinatesInTheEventAreUsedWithoutSearchingForThePlace() {
        var http = replay(A_ROUTE);
        assertThat(find(http, Destination.at(" " + PLACE_TEXT + " ", PLACE)))
                .isEqualTo(new RouteOutcome.Found(PLACE_TEXT, THE_ROUTE, Match.GIVEN, Measure.ROUTE));
        assertThat(http.requests).singleElement()
                .satisfies(request -> assertThat(request.uri().getPath()).isEqualTo("/v1/routing"));
        assertThat(http.requests.getFirst().uri().toString()).doesNotContain("Havenkade");
    }

    @Test
    void coordinatesWithoutALocationTextAreAPlaceWithoutAName() {
        assertThat(find(replay(A_ROUTE), Destination.at("", PLACE)))
                .isEqualTo(new RouteOutcome.Found("", THE_ROUTE, Match.GIVEN, Measure.ROUTE));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Microsoft Teams Meeting", "https://zoom.us/j/123456789", "  "})
    void anOnlineOrEmptyLocationIsNoPlaceAndNothingIsSent(String text) {
        var http = replay(ONE_PLACE, A_ROUTE);
        assertThat(find(http, Destination.of(text))).isEqualTo(new RouteOutcome.NotAPlace());
        assertThat(http.requests).isEmpty();
    }

    @Test
    void anOnlineLocationIsNoPlaceEvenWithCoordinates() {
        var http = replay(A_ROUTE);
        assertThat(find(http, Destination.at("Microsoft Teams Meeting", PLACE)))
                .isEqualTo(new RouteOutcome.NotAPlace());
        assertThat(http.requests).isEmpty();
    }

    @Test
    void withoutAnApiKeyNothingIsSentAndItMayBeAskedAgainAtOnce() {
        var http = replay(ONE_PLACE, A_ROUTE);
        var outcome = new RouteLookup(new Geoapify(http)).find(Destination.of(PLACE_TEXT), HOME, Map.of(), NOW);
        assertThat(outcome).isInstanceOfSatisfying(RouteOutcome.Failed.class,
                failed -> assertThat(failed.retryAt()).isEmpty());
        assertThat(http.requests).isEmpty();
    }

    @Test
    void aKeyTheServiceRefusesToSendIsAFailureThatMayBeRetriedAtOnce() {
        var http = replay(ONE_PLACE, A_ROUTE);
        var outcome = new RouteLookup(new Geoapify(http))
                .find(Destination.of(PLACE_TEXT), HOME, Map.of(CalendarSecrets.API_KEY, "not a key"), NOW);
        assertThat(outcome).isInstanceOfSatisfying(RouteOutcome.Failed.class,
                failed -> assertThat(failed.retryAt()).isEmpty());
        assertThat(http.requests).isEmpty();
    }

    static Stream<Arguments> failures() {
        return Stream.of(
                Arguments.of(recorded(401, "geoapify_401.json"), Duration.ofHours(1)),
                Arguments.of(new Reply(429, RecordedRoutes.answer("geoapify_429.json"), Map.of("Retry-After", "120")),
                        Duration.ofSeconds(120)),
                Arguments.of(recorded(429, "geoapify_429.json"), Duration.ofHours(1)),
                Arguments.of(new Reply(503, "Service Unavailable"), Duration.ofMinutes(5)),
                Arguments.of(new Reply(200, "{\"results\": "), Duration.ofHours(1)));
    }

    /** A refused key, too many requests, a server error and a malformed answer each leave the service alone a while. */
    @ParameterizedTest
    @MethodSource("failures")
    void afterAFailureTheServiceIsNotAskedUntilItsWaitHasPassed(Reply failure, Duration wait) {
        var http = new InTurn(failure, recorded(200, "geoapify_search_one.json"), recorded(200, "geoapify_route.json"));
        var lookup = new RouteLookup(new Geoapify(http));

        var failed = lookup.find(Destination.of(PLACE_TEXT), HOME, SETTINGS, NOW);
        var waiting = lookup.find(Destination.of(PLACE_TEXT), HOME, SETTINGS, NOW.plus(wait).minusSeconds(1));
        int sentWhileWaiting = http.requests.size();
        var again = lookup.find(Destination.of(PLACE_TEXT), HOME, SETTINGS, NOW.plus(wait));

        assertThat(failed).isInstanceOfSatisfying(RouteOutcome.Failed.class,
                f -> assertThat(f.retryAt()).contains(NOW.plus(wait)));
        assertThat(waiting).isInstanceOfSatisfying(RouteOutcome.Failed.class,
                f -> assertThat(f.retryAt()).contains(NOW.plus(wait)));
        assertThat(sentWhileWaiting).as("nothing sent while waiting").isEqualTo(1);
        assertThat(again).isEqualTo(new RouteOutcome.Found("Zeedorp, Nowhere", THE_ROUTE, Match.ONLY, Measure.ROUTE));
    }

    @Test
    void aBugInTheServiceIsAFailureThatTellsOnlyItsKind() {
        var outcome = new RouteLookup(new Geoapify(new InTurn())).find(Destination.of(PLACE_TEXT), HOME, SETTINGS, NOW);
        assertThat(outcome).isEqualTo(new RouteOutcome.Failed("an unexpected error (NoSuchElementException)",
                Optional.of(NOW.plus(Duration.ofHours(1)))));
    }

    @Test
    void aServiceThatStartsFailingAndOneThatAnswersAgainAreEachLoggedOnce() {
        var route = recorded(200, "geoapify_route.json");
        var lookup = new RouteLookup(new Geoapify(new InTurn(route, route, new Reply(503, ""), route)));
        for (int minutes : List.of(0, 10, 20, 30)) {
            lookup.find(Destination.at(PLACE_TEXT, PLACE), HOME, SETTINGS, NOW.plus(Duration.ofMinutes(minutes)));
        }
        assertThat(log.lines()).containsExactly("INFO Geoapify gives places and routes",
                "WARNING Geoapify gives no places or routes: Geoapify has a problem (status 503)",
                "INFO Geoapify gives places and routes again");
    }

    @Test
    void whatMayBeLoggedOfAnOutcomeOrADestinationHoldsNoPlaceOrCoordinates() {
        assertThat(List.of(new RouteOutcome.Found(PLACE_TEXT, THE_ROUTE, Match.CHOSEN, Measure.ROUTE).toString(),
                Destination.at(PLACE_TEXT, PLACE).toString(), Destination.of(PLACE_TEXT).toString(),
                new Candidate(PLACE_TEXT, PLACE).toString())).containsExactly("Found[CHOSEN, ROUTE]",
                "Destination[with coordinates]", "Destination[text only]", "Candidate[hidden]");
    }
}
