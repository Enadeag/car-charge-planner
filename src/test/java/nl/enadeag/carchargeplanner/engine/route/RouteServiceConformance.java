package nl.enadeag.carchargeplanner.engine.route;

import static nl.enadeag.carchargeplanner.engine.route.RouteServiceCase.HOME;
import static nl.enadeag.carchargeplanner.engine.route.RouteServiceCase.PLACE;
import static nl.enadeag.carchargeplanner.engine.route.RouteServiceCase.PLACE_TEXT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import nl.enadeag.carchargeplanner.LogCapture;
import nl.enadeag.carchargeplanner.engine.forecast.HttpGet;
import nl.enadeag.carchargeplanner.engine.forecast.ReplayHttp.Route;
import nl.enadeag.carchargeplanner.engine.forecast.SettingSpec;
import java.io.IOException;
import java.net.ConnectException;
import java.net.http.HttpTimeoutException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The conformance kit: what every route service must do, run against answers made in its documented shape. A
 * service's test class extends it and describes the service in {@link #serviceCase()}; nothing here touches the
 * network.
 */
public abstract class RouteServiceConformance {

    private static final Instant NOW = Instant.parse("2026-10-27T08:00:00Z");

    protected abstract RouteServiceCase serviceCase();

    /** Asks for both: a search and a route. */
    private interface Ask {
        void ask(RouteService service, Map<String, String> settings) throws RouteError;
    }

    static Stream<Ask> asks() {
        return Stream.of((service, settings) -> service.search(PLACE_TEXT, HOME, settings),
                (service, settings) -> service.drive(HOME, PLACE, settings));
    }

    @Test
    void declaresCompleteMetadata() {
        var info = serviceCase().service(serviceCase().ok()).service().info();
        assertThat(List.of(info.name(), info.attribution(), info.sends())).allMatch(t -> !t.isBlank());
        assertThat(info.termsUrl()).startsWith("https://");
        assertThat(info.settings()).extracting(SettingSpec::name).doesNotHaveDuplicates();
        assertThat(info.settings()).allMatch(s -> !s.name().isBlank() && !s.label().isBlank() && !s.hint().isBlank());
    }

    @Test
    void aGoodAnswerGivesPlacesAndADriveThroughItsHttpGet() throws RouteError {
        var c = serviceCase();
        var made = c.service(c.ok());

        var places = made.service().search(PLACE_TEXT, HOME, c.settings());
        var drive = made.service().drive(HOME, places.getFirst().location(), c.settings());

        assertThat(places).isNotEmpty().allSatisfy(place -> assertThat(place.name()).isNotBlank());
        assertThat(drive).hasValueSatisfying(d -> assertThat(d.distanceKm()).isPositive());
        assertThat(made.http().requests).as("asked through the HttpGet it was given").hasSize(2);
        assertThat(made.http().unexpected).isEmpty();
    }

    @Test
    void itLeavesItsSettingsAlone() throws RouteError {
        var c = serviceCase();
        var settings = new HashMap<>(c.settings());
        var service = c.service(c.ok()).service();
        service.search(PLACE_TEXT, HOME, settings);
        service.drive(HOME, PLACE, settings);
        assertThat(settings).isEqualTo(c.settings());
    }

    @ParameterizedTest
    @MethodSource("asks")
    void aRateLimitIsRateLimitedWithTheRetryTime(Ask ask) {
        var c = serviceCase();
        var error = (RouteError.RateLimited) fails(ask, c.rateLimited(), RouteError.RateLimited.class);
        assertThat(error.retryAfter().orElse(null)).isEqualTo(c.retryAfter());
    }

    @ParameterizedTest
    @MethodSource("asks")
    void aRefusedKeyIsRefused(Ask ask) {
        fails(ask, serviceCase().refused(), RouteError.Refused.class);
    }

    @ParameterizedTest
    @MethodSource("asks")
    void aServerErrorIsUnavailable(Ask ask) {
        fails(ask, serviceCase().serverError(), RouteError.Unavailable.class);
    }

    static Stream<IOException> noNetwork() {
        return Stream.of(new ConnectException("unreachable"), new HttpTimeoutException("slow"),
                new IOException("reset"), new HttpGet.TooLarge(HttpGet.DEFAULT_MAX_BODY));
    }

    @ParameterizedTest
    @MethodSource("noNetwork")
    void noNetworkIsUnavailable(IOException failure) {
        asks().forEach(ask -> fails(ask, List.of(Route.failure(failure)), RouteError.Unavailable.class));
    }

    @Test
    void itWaitsAndReadsNoMoreThanAForecastProviderMay() throws RouteError {
        var c = serviceCase();
        var made = c.service(c.ok());
        made.service().search(PLACE_TEXT, HOME, c.settings());
        made.service().drive(HOME, PLACE, c.settings());
        assertThat(made.http().requests).allSatisfy(request -> {
            assertThat(request.maxBody().bytes()).isLessThanOrEqualTo(HttpGet.DEFAULT_MAX_BODY.bytes());
            assertThat(request.timeout()).isLessThanOrEqualTo(HttpGet.DEFAULT_TIMEOUT);
        });
    }

    @ParameterizedTest
    @MethodSource("asks")
    void anAnswerThatIsNotAsDocumentedIsABadResponse(Ask ask) {
        fails(ask, serviceCase().badBody(), RouteError.BadResponse.class);
    }

    @Test
    void anEmptyAnswerNeverCrashes() {
        var c = serviceCase();
        asks().forEach(ask -> {
            var made = c.service(c.emptyBody());
            var thrown = catchThrowable(() -> ask.ask(made.service(), c.settings()));
            assertThat(thrown).as("nothing, or a route error").satisfiesAnyOf(
                    t -> assertThat(t).isNull(), t -> assertThat(t).isInstanceOf(RouteError.class));
            assertThat(made.http().unexpected).isEmpty();
        });
    }

    @Test
    void missingRequiredSettingsAreReportedBeforeAnythingIsSent() {
        var c = serviceCase();
        var made = c.service(c.ok());
        var required = made.service().info().settings().stream().filter(s -> !s.optional()).map(SettingSpec::name)
                .toList();
        assumeFalse(required.isEmpty(), "this service has no required settings");
        for (var name : required) {
            var settings = new HashMap<>(c.settings());
            settings.remove(name);
            assertThatThrownBy(() -> made.service().search(PLACE_TEXT, HOME, settings))
                    .isInstanceOf(RouteError.MissingSetting.class);
            assertThatThrownBy(() -> made.service().drive(HOME, PLACE, settings))
                    .isInstanceOf(RouteError.MissingSetting.class);
        }
        assertThat(made.http().requests).isEmpty();
    }

    /**
     * A keychain edited by hand can hold what Settings would refuse: a line break in a value must not reach a
     * request, where it could end a header or the address and start one of its own.
     */
    @Test
    void aSettingWithALineBreakIsRefusedOrNeverReachesARequest() {
        var c = serviceCase();
        var wrong = "ftp://x y/?a=1&b=2#\r\nX-Injected: 1";
        for (var spec : c.service(c.ok()).service().info().settings()) {
            assertThat(spec.format().accepts(wrong)).as("%s refuses the value", spec.name()).isFalse();
            var settings = new HashMap<>(c.settings());
            settings.put(spec.name(), wrong);
            asks().forEach(ask -> {
                var made = c.service(c.ok());
                var thrown = catchThrowable(() -> ask.ask(made.service(), settings));
                if (thrown instanceof RouteError.MissingSetting) {
                    assertThat(made.http().requests).as("%s: nothing sent", spec.name()).isEmpty();
                }
                assertThat(made.http().requests).allSatisfy(request -> {
                    assertThat(request.uri().toString()).as("%s: address", spec.name())
                            .doesNotContainPattern("[\\r\\n]");
                    assertThat(request.headers().values()).as("%s: headers", spec.name())
                            .noneMatch(value -> value.contains("\r") || value.contains("\n"));
                });
            });
        }
    }

    /**
     * Whatever happens, the lookup's reasons and the log never show what was sent: the API key, the location text,
     * the home's or the place's coordinates, or the address of a request; also not when the server echoes them.
     */
    @Test
    void theKeyTheLocationTheCoordinatesAndTheAddressNeverReachLogsOrReasons() {
        var c = serviceCase();
        var shown = new ArrayList<String>();
        var addresses = new ArrayList<String>();
        try (var log = LogCapture.of("nl.enadeag.carchargeplanner")) {
            for (var routes : scenarios(c)) {
                for (var destination : List.of(Destination.of(PLACE_TEXT), Destination.at(PLACE_TEXT, PLACE))) {
                    var made = c.service(routes);
                    var outcome = new RouteLookup(made.service()).find(destination, HOME, c.settings(), NOW);
                    shown.add(outcome.toString());
                    if (outcome instanceof RouteOutcome.Failed failed) {
                        shown.add(failed.reason());
                    }
                    made.http().requests.forEach(request -> {
                        addresses.add(request.uri().toString());
                        addresses.add(request.uri().getRawQuery());
                    });
                }
            }
            log.records().forEach(record -> {
                shown.add(record.getMessage());
                if (record.getThrown() != null) {
                    shown.add(String.valueOf(record.getThrown()));
                }
            });
        }
        assertThat(shown).as("what was logged and told").isNotEmpty();
        assertThat(addresses).as("the requests sent").isNotEmpty();
        var coordinate = Pattern.compile("51\\.2345|5\\.6789|51\\.7345");
        var secrets = c.service(c.ok()).service().info().secretValues(c.settings());
        assertThat(secrets).isNotEmpty();
        for (var text : shown) {
            assertThat(addresses).as("an address in: %s", text).noneMatch(address -> address != null
                    && text.contains(address));
            assertThat(coordinate.matcher(text).find()).as("a coordinate in: %s", text).isFalse();
            assertThat(text).doesNotContain(PLACE_TEXT).doesNotContain("Havenkade");
            secrets.forEach(secret -> assertThat(text).doesNotContain(secret));
        }
    }

    private static List<List<Route>> scenarios(RouteServiceCase c) {
        var echo = new StringBuilder("you sent " + PLACE_TEXT + " near 51.23456,5.67891 to 51.73456,5.67891");
        c.settings().values().forEach(value -> echo.append(' ').append(value));
        return List.of(c.ok(), c.serverError(), c.badBody(), c.emptyBody(), c.rateLimited(), c.refused(),
                List.of(Route.failure(new ConnectException("down"))), c.errorSaying().apply(echo.toString()));
    }

    private RouteError fails(Ask ask, List<Route> routes, Class<? extends RouteError> kind) {
        var c = serviceCase();
        var made = c.service(routes);
        var error = catchThrowable(() -> ask.ask(made.service(), c.settings()));
        assertThat(error).isInstanceOf(kind);
        assertThat(made.http().unexpected).isEmpty();
        return (RouteError) error;
    }

    @ParameterizedTest
    @CsvSource({"500, true", "499, false"})
    void aSearchStatusFrom500OnIsTheServersProblemAndBelowItARefusal(int status, boolean serverProblem) {
        fails(asks().findFirst().orElseThrow(), List.of(Route.answer("", new HttpGet.Reply(status, ""))),
                serverProblem ? RouteError.Unavailable.class : RouteError.BadResponse.class);
    }
}
