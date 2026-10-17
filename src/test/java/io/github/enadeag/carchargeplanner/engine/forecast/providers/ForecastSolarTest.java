package io.github.enadeag.carchargeplanner.engine.forecast.providers;

import static io.github.enadeag.carchargeplanner.engine.forecast.ProviderCase.EXAMPLE_SYSTEM;
import static io.github.enadeag.carchargeplanner.engine.forecast.ReplayHttp.Route.answer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.Assertions.within;

import io.github.enadeag.carchargeplanner.engine.config.Coordinates;
import io.github.enadeag.carchargeplanner.engine.config.PanelArray;
import io.github.enadeag.carchargeplanner.engine.forecast.ForecastStep;
import io.github.enadeag.carchargeplanner.engine.forecast.HttpGet;
import io.github.enadeag.carchargeplanner.engine.forecast.HttpGet.Reply;
import io.github.enadeag.carchargeplanner.engine.forecast.ProviderCase;
import io.github.enadeag.carchargeplanner.engine.forecast.ProviderConformance;
import io.github.enadeag.carchargeplanner.engine.forecast.ProviderError;
import io.github.enadeag.carchargeplanner.engine.forecast.Recorded;
import io.github.enadeag.carchargeplanner.engine.forecast.ReplayHttp;
import io.github.enadeag.carchargeplanner.engine.forecast.SettingFormat;
import io.github.enadeag.carchargeplanner.engine.forecast.Steps;
import io.github.enadeag.carchargeplanner.engine.forecast.SystemDescription;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

class ForecastSolarTest extends ProviderConformance {

    private static final String RECORDED = Recorded.answer("forecast_solar_2days.json");
    private static final String RATE_LIMITED = Recorded.answer("forecast_solar_429.json");
    private static final Instant NOW = Instant.parse("2026-10-07T07:52:51Z"); // when the answer was recorded
    private static final String RETRY_AT = "2026-10-07T10:19:59+02:00"; // in the 429 answer: 08:19:59 UTC
    private static final String SECRET = "FsSecretKey5555";
    private static final ZoneId AMSTERDAM = ZoneId.of("Europe/Amsterdam");
    private static final SystemDescription TWO_ARRAYS = EXAMPLE_SYSTEM.withArrays(List.of(
            new PanelArray("", 3.0, 30, -90, 0.14), new PanelArray("", 2.0, 10, 90, 0.14)));

    @Override
    protected ProviderCase providerCase() {
        return new ProviderCase(ForecastSolar::new,
                List.of(answer("estimate/watthours/period", new Reply(200, RECORDED))),
                List.of(answer("", new Reply(502, "Bad Gateway"))),
                List.of(answer("", new Reply(200, "<html>maintenance</html>"))),
                List.of(answer("", new Reply(200, "{}"))),
                List.of(answer("", new Reply(429, RATE_LIMITED, Map.of("X-Ratelimit-Retry-At", RETRY_AT)))),
                Duration.ofMinutes(27).plusSeconds(8), Map.of(), Map.of("api_key", SECRET), NOW,
                text -> List.of(answer("", new Reply(422, refusal(text)))));
    }

    private static String refusal(String text) {
        var body = new JsonMapper().createObjectNode();
        body.putObject("message").put("code", 422).put("type", "validation error").put("text", text);
        return body.toString();
    }

    private static List<ForecastStep> fetch(String body, SystemDescription system) throws ProviderError {
        return new ForecastSolar(ReplayHttp.always(new Reply(200, body))).fetch(system, Map.of(), NOW);
    }

    private static String requested(SystemDescription system, Map<String, String> settings) throws ProviderError {
        var http = ReplayHttp.always(new Reply(200, RECORDED));
        new ForecastSolar(http).fetch(system, settings, NOW);
        return http.requests.getFirst().uri().toString();
    }

    private static String local(Instant moment) {
        return LocalTime.ofInstant(moment, AMSTERDAM).toString();
    }

    @Test
    void eachKeyAfterTheFirstOfADayIsAStepOfTheEnergySinceTheKeyBefore() throws ProviderError {
        var steps = fetch(RECORDED, EXAMPLE_SYSTEM);

        assertThat(steps).hasSize(26); // two days of 14 keys; the sunrise key of each has nothing before it
        var first = steps.getFirst();
        assertThat(local(first.start()) + "-" + local(first.end())).isEqualTo("07:51:52-08:00");
        assertThat(first.powerKw()).isCloseTo(0.010 / (488 / 3600.0), within(1e-9)); // 10 Wh in 8 min 8 s
        var nine = steps.stream().filter(s -> s.end().equals(Instant.parse("2026-10-07T07:00:00Z"))).findFirst().orElseThrow();
        assertThat(local(nine.start())).isEqualTo("08:00");
        assertThat(nine.powerKw()).isCloseTo(0.332, within(1e-9));
        var eleven = steps.stream().filter(s -> s.end().equals(Instant.parse("2026-10-07T09:00:00Z"))).findFirst().orElseThrow();
        assertThat(eleven.powerKw()).isCloseTo(1.574, within(1e-9));
    }

    @Test
    void theEnergyOfTheStepsIsTheEnergyOfTheAnswer() throws ProviderError {
        var steps = fetch(RECORDED, EXAMPLE_SYSTEM);
        var result = (ObjectNode) new JsonMapper().readTree(RECORDED).path("result");
        double expected = 0;
        String day = "";
        for (var entry : result.properties()) {
            boolean sunrise = !entry.getKey().startsWith(day); // the sunrise key's 0 starts each day
            day = entry.getKey().substring(0, 10);
            expected += sunrise ? 0 : entry.getValue().doubleValue();
        }
        double delivered = steps.stream().mapToDouble(s -> s.powerKw() * s.hours()).sum() * 1000;
        assertThat(delivered).isCloseTo(expected, within(1e-6));
    }

    @Test
    void nightGivesNoStepsAndTheStepsKeepTheContract() throws ProviderError {
        var steps = fetch(RECORDED, EXAMPLE_SYSTEM);
        assertThat(Steps.problems(steps)).isEmpty();
        var gaps = new ArrayList<Duration>();
        for (int i = 1; i < steps.size(); i++) {
            if (!steps.get(i).start().equals(steps.get(i - 1).end())) {
                gaps.add(Duration.between(steps.get(i - 1).end(), steps.get(i).start()));
            }
        }
        assertThat(gaps).singleElement().satisfies(gap -> assertThat(gap).isGreaterThan(Duration.ofHours(10)));
    }

    @Test
    void theTimeZoneOfThePlaceInTheAnswerIsWhatTheKeysAreReadIn() throws ProviderError {
        var body = RECORDED.replace("\"timezone\": \"Europe/Amsterdam\"", "\"timezone\": \"America/New_York\"");
        assertThat(body).isNotEqualTo(RECORDED);
        // 07:51 in New York (summer time) is 11:51 UTC
        assertThat(fetch(body, EXAMPLE_SYSTEM).getFirst().start()).isEqualTo(Instant.parse("2026-10-07T11:51:52Z"));
    }

    @Test
    void theSystemLossesSettingIsNotApplied() throws ProviderError {
        var lossy = EXAMPLE_SYSTEM.withArrays(List.of(new PanelArray("", 4.8, 35, -45, 0.5)));
        assertThat(fetch(RECORDED, lossy)).isEqualTo(fetch(RECORDED, EXAMPLE_SYSTEM));
    }

    @Test
    void acrossTheMissingHourAPeriodIsTheRealElapsedTime() throws ProviderError {
        var steps = fetch(Recorded.answer("forecast_solar_dst_spring.json"), EXAMPLE_SYSTEM);
        assertThat(Steps.problems(steps)).isEmpty();
        var across = steps.getFirst(); // 01:30 (CET) to 03:30 (CEST) on 2026-03-29: one hour has gone, not two
        assertThat(across.start()).isEqualTo(Instant.parse("2026-03-29T00:30:00Z"));
        assertThat(across.end()).isEqualTo(Instant.parse("2026-03-29T01:30:00Z"));
        assertThat(across.powerKw()).isCloseTo(0.120, within(1e-9)); // 120 Wh in that hour
    }

    @Test
    void acrossTheRepeatedHourAPeriodIsTheRealElapsedTime() throws ProviderError {
        var steps = fetch(Recorded.answer("forecast_solar_dst_autumn.json"), EXAMPLE_SYSTEM);
        assertThat(Steps.problems(steps)).isEmpty();
        // 01:30 CEST, then the first 02:30 (CEST), then 03:30 CET: the repeated hour is counted
        var first = steps.get(0);
        var second = steps.get(1);
        assertThat(first.hours()).isCloseTo(1.0, within(1e-9));
        assertThat(second.hours()).isCloseTo(2.0, within(1e-9));
        assertThat(first.powerKw()).isCloseTo(0.060, within(1e-9)); // 60 Wh in 1 h
        assertThat(second.powerKw()).isCloseTo(0.045, within(1e-9)); // 90 Wh in 2 h
        assertThat(first.end()).isEqualTo(second.start());
    }

    @Test
    void thePublicRequestHasTheDocumentedShape() throws ProviderError {
        var http = ReplayHttp.always(new Reply(200, RECORDED));
        new ForecastSolar(http).fetch(EXAMPLE_SYSTEM, Map.of(), NOW);
        var request = http.requests.getFirst();
        assertThat(request.uri()).hasToString("https://api.forecast.solar/estimate/watthours/period/52.37/4.9/35/-45/4.8");
        assertThat(request.headers()).containsEntry("Accept", "application/json");
    }

    @Test
    void aKeyGoesInFrontOfTheRouteAsDocumented() throws ProviderError {
        assertThat(requested(EXAMPLE_SYSTEM, Map.of("api_key", " " + SECRET + " ")))
                .isEqualTo("https://api.forecast.solar/" + SECRET + "/estimate/watthours/period/52.37/4.9/35/-45/4.8");
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc/../estimate", "key?x=1", "key#x", "with space", "k%2Fey", "sleutel-1",
            "ключ", "a12345678901234567890123456789012345678901234567890123456789012345"})
    void aKeyThatIsNotOnlyLettersAndDigitsIsRefusedBeforeAnythingIsSent(String key) {
        var http = ReplayHttp.always(new Reply(200, RECORDED));
        assertThatThrownBy(() -> new ForecastSolar(http).fetch(EXAMPLE_SYSTEM, Map.of("api_key", key), NOW))
                .isInstanceOf(ProviderError.NotConfigured.class).hasMessageContaining("letters and digits")
                .message().doesNotContain(key);
        assertThat(http.requests).isEmpty();
    }

    @Test
    void itsKeyIsCheckedForLettersAndDigitsWhenEntered() {
        assertThat(ForecastSolar.INFO.settings().getFirst().format()).isEqualTo(SettingFormat.LETTERS_AND_DIGITS);
    }

    @Test
    void aBlankKeyMeansThePublicPlan() throws ProviderError {
        assertThat(requested(EXAMPLE_SYSTEM, Map.of("api_key", "  "))).startsWith("https://api.forecast.solar/estimate/");
    }

    /** Both use 0 = south, -90 = east, 90 = west and 180 / -180 = north, so it passes through unchanged. */
    @ParameterizedTest
    @ValueSource(ints = {0, -90, 90, 180, -180, -45})
    void ourAzimuthIsTheirs(int azimuth) throws ProviderError {
        var system = EXAMPLE_SYSTEM.withArrays(List.of(new PanelArray("", 4.8, 35, azimuth, 0.14)));
        var parts = ForecastSolar.requestUri(system, system.arrays().getFirst(), Map.of()).toString().split("/");
        assertThat(parts[parts.length - 2]).isEqualTo(String.valueOf(azimuth));
    }

    @ParameterizedTest
    @CsvSource({
        "52.37123456, -4.123456, 4.123456, 34.6, 12.4, /52.3712/-4.1235/35/12/4.123",
        "52.0, 4.0, 5.0, 90, 180, /52/4/90/180/5",
        "52.0, 4.0, 5.0, 2.5, 0.5, /52/4/2/0/5",
    })
    void numbersAreWholeDegreesAndShortDecimals(double lat, double lon, double kwp, double tilt, double azimuth,
            String end) throws ProviderError {
        var system = new SystemDescription(new Coordinates(lat, lon), AMSTERDAM, List.of(new PanelArray("", kwp, tilt, azimuth, 0.14)));
        assertThat(ForecastSolar.requestUri(system, system.arrays().getFirst(), Map.of()).toString()).endsWith(end);
    }

    private static Duration rateLimit(Map<String, String> headers, String body, Instant now) {
        var http = ReplayHttp.always(new Reply(429, body, headers));
        var error = catchThrowableOfType(ProviderError.RateLimited.class,
                () -> new ForecastSolar(http).fetch(EXAMPLE_SYSTEM, Map.of(), now));
        return error.retryAfter().orElse(null);
    }

    @Test
    void theRetryTimeComesFromTheHeaderFirst() {
        assertThat(rateLimit(Map.of("X-Ratelimit-Retry-At", "2026-10-07T10:30:00+02:00"), RATE_LIMITED, NOW))
                .isEqualTo(Duration.ofMinutes(37).plusSeconds(9));
    }

    @Test
    void elseFromTheBody() {
        assertThat(rateLimit(Map.of(), RATE_LIMITED, NOW)).isEqualTo(Duration.ofMinutes(27).plusSeconds(8));
    }

    @Test
    void aRetryTimeAlreadyPastIsZero() {
        assertThat(rateLimit(Map.of(), RATE_LIMITED, Instant.parse("2026-10-07T12:00:00Z"))).isEqualTo(Duration.ZERO);
    }

    static Stream<Arguments> unusableRetryTimes() {
        return Stream.of(Arguments.of(Map.of(), "{}"), Arguments.of(Map.of(), "not json"),
                Arguments.of(Map.of("X-Ratelimit-Retry-At", "soon"), "{}"),
                Arguments.of(Map.of("X-Ratelimit-Retry-At", "2026-10-07T10:00:00"), "{}"));
    }

    @ParameterizedTest
    @MethodSource("unusableRetryTimes")
    void withoutAUsableRetryTimeThereIsNone(Map<String, String> headers, String body) {
        assertThat(rateLimit(headers, body, NOW)).isNull();
    }

    @Test
    void aRejectedRequestKeepsTheReasonTheServerGives() {
        var body = "{\"message\": {\"code\": 422, \"type\": \"validation error\", "
                + "\"text\": \"Latitude must be between -90 and 90\"}}";
        assertThatThrownBy(() -> new ForecastSolar(ReplayHttp.always(new Reply(422, body))).fetch(EXAMPLE_SYSTEM, Map.of(), NOW))
                .isInstanceOf(ProviderError.BadResponse.class).hasMessageContaining(" says: Latitude must be");
    }

    @Test
    void aRefusedKeyPointsAtTheSettings() {
        assertThatThrownBy(() -> new ForecastSolar(ReplayHttp.always(new Reply(401, ""))).fetch(EXAMPLE_SYSTEM, Map.of(), NOW))
                .isInstanceOf(ProviderError.BadResponse.class).hasMessageContaining("API key");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{\"result\": {}, \"message\": {\"code\": 0}}",
        "{\"result\": [], \"message\": {\"code\": 0}}",
        "{\"message\": {\"code\": 0}}",
        "{\"result\": {\"2026-10-07 08:00:00\": 1}}",
        "{\"result\": {\"yesterday\": 1}, \"message\": {\"code\": 0}}",
        "{\"result\": {\"2026-10-07 08:00:00\": \"lots\", \"2026-10-07 09:00:00\": 1}, \"message\": {\"code\": 0}}",
        "{\"result\": {\"2026-10-07 08:00:00\": 0, \"2026-10-07 09:00:00\": -5}, \"message\": {\"code\": 0}}",
        "{\"result\": {\"2026-10-07 08:00:00\": 0, \"2026-10-07 09:00:00\": 1}, "
                + "\"message\": {\"code\": 0, \"info\": {\"timezone\": \"Mars/Olympus\"}}}",
        "{\"result\": {\"2026-10-07 08:00:00\": 0}, \"message\": {\"code\": 2, \"type\": \"error\", "
                + "\"text\": \"no data for this place\"}}",
        "[1, 2]",
    })
    void anAnswerThatIsNotAsDocumentedIsABadResponse(String body) {
        assertThatThrownBy(() -> fetch(body, EXAMPLE_SYSTEM)).isInstanceOf(ProviderError.BadResponse.class);
    }

    @Test
    void thePublicPlanIsAskedOnceForEachArrayAndTheEnergyIsAddedUp() throws ProviderError {
        var http = ReplayHttp.always(new Reply(200, RECORDED));
        var steps = new ForecastSolar(http).fetch(TWO_ARRAYS, Map.of(), NOW);

        assertThat(http.requests).extracting(r -> r.uri().toString().split("period/")[1])
                .containsExactly("52.37/4.9/30/-90/3", "52.37/4.9/10/90/2");
        var single = fetch(RECORDED, EXAMPLE_SYSTEM);
        assertThat(steps).hasSameSizeAs(single);
        for (int i = 0; i < steps.size(); i++) {
            assertThat(steps.get(i).powerKw()).isCloseTo(2 * single.get(i).powerKw(), within(1e-9));
        }
        assertThat(Steps.problems(steps)).isEmpty();
    }

    @Test
    void aKeyIsSentWithEveryRequest() throws ProviderError {
        var http = ReplayHttp.always(new Reply(200, RECORDED));
        new ForecastSolar(http).fetch(TWO_ARRAYS, Map.of("api_key", SECRET), NOW);
        assertThat(http.requests).hasSize(2).allMatch(r -> r.uri().toString().contains("/" + SECRET + "/estimate/"));
    }

    @Test
    void aRateLimitOnTheSecondArrayIsARateLimitForTheWholeForecast() {
        var answers = List.of(new Reply(200, RECORDED), new Reply(429, RATE_LIMITED, Map.of("X-Ratelimit-Retry-At", RETRY_AT)));
        var asked = new ArrayList<HttpGet.Request>();
        HttpGet http = request -> {
            asked.add(request);
            return answers.get(asked.size() - 1);
        };
        var error = catchThrowableOfType(ProviderError.RateLimited.class,
                () -> new ForecastSolar(http).fetch(TWO_ARRAYS, Map.of(), NOW));
        assertThat(error.retryAfter()).contains(Duration.ofMinutes(27).plusSeconds(8));
        assertThat(asked).hasSize(2);
    }
}
