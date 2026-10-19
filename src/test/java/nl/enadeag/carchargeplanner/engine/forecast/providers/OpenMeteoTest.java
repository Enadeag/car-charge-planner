package nl.enadeag.carchargeplanner.engine.forecast.providers;

import static nl.enadeag.carchargeplanner.engine.forecast.ProviderCase.EXAMPLE_SYSTEM;
import static nl.enadeag.carchargeplanner.engine.forecast.ReplayHttp.Route.answer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assertions.within;

import nl.enadeag.carchargeplanner.engine.config.PanelArray;
import nl.enadeag.carchargeplanner.engine.forecast.ForecastStep;
import nl.enadeag.carchargeplanner.engine.forecast.HttpGet.Reply;
import nl.enadeag.carchargeplanner.engine.forecast.ProviderCase;
import nl.enadeag.carchargeplanner.engine.forecast.ProviderConformance;
import nl.enadeag.carchargeplanner.engine.forecast.ProviderError;
import nl.enadeag.carchargeplanner.engine.forecast.Recorded;
import nl.enadeag.carchargeplanner.engine.forecast.ReplayHttp;
import nl.enadeag.carchargeplanner.engine.forecast.SettingFormat;
import nl.enadeag.carchargeplanner.engine.forecast.SettingSpec;
import nl.enadeag.carchargeplanner.engine.forecast.SolarSite;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

class OpenMeteoTest extends ProviderConformance {

    private static final String RECORDED = Recorded.answer("open_meteo_3days.json");
    private static final Instant NOW = Instant.parse("2026-10-06T07:00:00Z");
    private static final String SECRET = "OM-SECRET-KEY-9876";

    @Override
    protected ProviderCase providerCase() {
        return new ProviderCase(OpenMeteo::new,
                List.of(answer("/v1/forecast", new Reply(200, RECORDED))),
                List.of(answer("", new Reply(503, "Service Unavailable"))),
                List.of(answer("", new Reply(200, "<html>maintenance</html>"))),
                List.of(answer("", new Reply(200, "{}"))),
                List.of(answer("", new Reply(429, "{\"error\": true, \"reason\": \"limit\"}", Map.of("Retry-After", "90")))),
                Duration.ofSeconds(90), Map.of(), Map.of("api_key", SECRET), NOW,
                text -> List.of(answer("", new Reply(400, refusal(text)))));
    }

    private static String refusal(String reason) {
        return new JsonMapper().createObjectNode().put("error", true).put("reason", reason).toString();
    }

    private static List<ForecastStep> fetch(String body, Map<String, String> settings, SolarSite system)
            throws ProviderError {
        return new OpenMeteo(ReplayHttp.always(new Reply(200, body))).fetch(system, settings, NOW);
    }

    private static List<ForecastStep> fetch(SolarSite system) throws ProviderError {
        return fetch(RECORDED, Map.of(), system);
    }

    private static ReplayHttp asked(Map<String, String> settings) throws ProviderError {
        var http = ReplayHttp.always(new Reply(200, RECORDED));
        new OpenMeteo(http).fetch(EXAMPLE_SYSTEM, settings, NOW);
        return http;
    }

    private static Map<String, String> query(URI uri) {
        return Arrays.stream(uri.getRawQuery().split("&")).map(pair -> pair.split("=", 2))
                .collect(Collectors.toMap(pair -> pair[0], pair -> URLDecoder.decode(pair[1], StandardCharsets.UTF_8)));
    }

    private static SolarSite withArrays(PanelArray... arrays) {
        return EXAMPLE_SYSTEM.withArrays(List.of(arrays));
    }

    private static double peak(List<ForecastStep> steps) {
        return steps.stream().mapToDouble(ForecastStep::powerKw).max().orElseThrow();
    }

    @Test
    void theValueStampedAtNineCoversTheHourBeforeItAndBecomesPower() throws ProviderError {
        var steps = fetch(EXAMPLE_SYSTEM);

        assertThat(steps).hasSize(72).allSatisfy(s -> assertThat(Duration.between(s.start(), s.end())).hasHours(1));
        // 09:00 summer time in Amsterdam; the recording has 156.6 W/m2 there
        var nine = steps.stream().filter(s -> s.end().equals(Instant.parse("2026-10-06T07:00:00Z"))).findFirst().orElseThrow();
        assertThat(nine.start()).isEqualTo(Instant.parse("2026-10-06T06:00:00Z"));
        assertThat(nine.powerKw()).isCloseTo(4.8 * 0.1566 * 0.86, within(1e-9));
    }

    @Test
    void lossesAndSizeAreAppliedByTheProvider() throws ProviderError {
        var small = fetch(withArrays(new PanelArray("", 2.4, 35, -45, 0.14)));
        var lossless = fetch(withArrays(new PanelArray("", 4.8, 35, -45, 0.0)));
        var base = fetch(EXAMPLE_SYSTEM);

        assertThat(peak(small)).isCloseTo(peak(base) / 2, within(1e-9));
        assertThat(peak(lossless)).isCloseTo(peak(base) / 0.86, within(1e-9));
        assertThat(OpenMeteo.powerKw(1000, 4.8, 0.14)).isCloseTo(4.128, within(1e-9));
    }

    @Test
    void aNullAtTheEdgeOfTheHorizonIsNoPower() throws ProviderError {
        var body = "{\"hourly\": {\"time\": [1791237600], \"global_tilted_irradiance\": [null]}}";
        assertThat(fetch(body, Map.of(), EXAMPLE_SYSTEM)).singleElement().extracting(ForecastStep::powerKw).isEqualTo(0.0);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{\"hourly\": {\"time\": [1791237600, 1791241200], \"global_tilted_irradiance\": [1.0]}}",
        "{\"hourly\": {\"time\": [\"soon\"], \"global_tilted_irradiance\": [1.0]}}",
        "{\"hourly\": {\"time\": [1791237600], \"global_tilted_irradiance\": [\"lots\"]}}",
        "{\"hourly\": {\"time\": [1791241200, 1791237600], \"global_tilted_irradiance\": [1.0, 1.0]}}",
        "{\"hourly\": {\"time\": [1791237600], \"global_tilted_irradiance\": [-50.0]}}",
        "{\"hourly\": {\"time\": [1000000000000000000000000000000], \"global_tilted_irradiance\": [1.0]}}",
        "[1, 2, 3]",
        "{\"error\": true, \"reason\": \"Latitude must be in range of -90 to 90°.\"}",
    })
    void anAnswerThatIsNotAsDocumentedIsABadResponse(String body) {
        assertThatThrownBy(() -> fetch(body, Map.of(), EXAMPLE_SYSTEM)).isInstanceOf(ProviderError.BadResponse.class);
    }

    @Test
    void theReasonTheServerGivesForARefusalIsKept() {
        var body = "{\"error\": true, \"reason\": \"Latitude must be in range of -90 to 90°.\"}";
        assertThatThrownBy(() -> new OpenMeteo(ReplayHttp.always(new Reply(400, body))).fetch(EXAMPLE_SYSTEM, Map.of(), NOW))
                .isInstanceOf(ProviderError.BadResponse.class).hasMessageContaining(" says: Latitude must be");
    }

    @Test
    void aRefusedKeyPointsAtTheSettings() {
        assertThatThrownBy(() -> new OpenMeteo(ReplayHttp.always(new Reply(401, ""))).fetch(EXAMPLE_SYSTEM, Map.of(), NOW))
                .isInstanceOf(ProviderError.BadResponse.class).hasMessageContaining("API key");
    }

    @Test
    void theRetryTimeIsNoneWhenTheServerGivesNone() {
        assertThatThrownBy(() -> new OpenMeteo(ReplayHttp.always(new Reply(429, ""))).fetch(EXAMPLE_SYSTEM, Map.of(), NOW))
                .isInstanceOfSatisfying(ProviderError.RateLimited.class, e -> assertThat(e.retryAfter()).isEmpty());
    }

    @Test
    void theRequestDescribesTheSystemAndAsksOnlyForIrradiance() throws ProviderError {
        var uri = asked(Map.of()).requests.getFirst().uri();

        assertThat(uri.getScheme() + "://" + uri.getHost() + uri.getPath()).isEqualTo("https://api.open-meteo.com/v1/forecast");
        assertThat(query(uri)).isEqualTo(Map.of("latitude", "52.37", "longitude", "4.9",
                "hourly", "global_tilted_irradiance", "tilt", "35", "azimuth", "-45", "timezone", "Europe/Amsterdam",
                "forecast_days", "4", "timeformat", "unixtime")); // no cloud cover, no key
    }

    @Test
    void aServerAddressReplacesThePublicOneAndAKeyIsSentAsApikey() throws ProviderError {
        var uri = asked(Map.of("base_url", "https://customer-api.open-meteo.com/", "api_key", SECRET)).requests.getFirst().uri();

        assertThat(uri.toString()).startsWith("https://customer-api.open-meteo.com/v1/forecast?");
        assertThat(query(uri)).containsEntry("apikey", SECRET);
    }

    @Test
    void blankSettingsMeanThePublicServiceWithoutAKey() throws ProviderError {
        var uri = asked(Map.of("base_url", "  ", "api_key", "")).requests.getFirst().uri();

        assertThat(uri.toString()).startsWith("https://api.open-meteo.com/");
        assertThat(query(uri)).doesNotContainKey("apikey");
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://api.open-meteo.com", "ftp://x", "api.open-meteo.com", "javascript:alert(1)",
            "http://localhost.evil.example", "http://localhost@evil.example", "http://127.0.0.1.nip.io",
            "https://user:secret@customer-api.open-meteo.com", "https://customer-api.open-meteo.com/?x=1",
            "https://customer-api.open-meteo.com#here", "https://", "http://[::2]:8080", "https://exa mple.org"})
    void anAddressAKeyMayNotBeSentToIsRefusedBeforeAnythingIsSent(String address) {
        var http = ReplayHttp.always(new Reply(200, RECORDED));
        assertThatThrownBy(() -> new OpenMeteo(http).fetch(EXAMPLE_SYSTEM, Map.of("base_url", address), NOW))
                .isInstanceOf(ProviderError.MissingSetting.class).hasMessageContaining("https");
        assertThat(http.requests).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://localhost:8080", "http://127.0.0.1:8080", "http://[::1]:8080", "http://LOCALHOST",
            "http://localhost:8080/open-meteo"})
    void aServerOfYourOwnOnThisComputerMayUsePlainHttp(String address) throws ProviderError {
        assertThat(asked(Map.of("base_url", address)).requests.getFirst().uri().toString())
                .startsWith(address + "/v1/forecast?");
    }

    @Test
    void itsServerAddressIsCheckedWhenEntered() {
        assertThat(OpenMeteo.INFO.settings().getFirst().format()).isEqualTo(SettingFormat.SERVER_ADDRESS);
    }

    @Test
    void eachArrayIsOneRequestWithItsOwnTiltAndDirectionAndThePowerIsAddedUp() throws ProviderError {
        var http = ReplayHttp.always(new Reply(200, RECORDED));
        var system = withArrays(new PanelArray("East", 3.0, 30, -90, 0.14), new PanelArray("West", 2.0, 10, 90, 0.0));
        var steps = new OpenMeteo(http).fetch(system, Map.of(), NOW);

        assertThat(http.requests).extracting(r -> query(r.uri()).get("tilt") + "/" + query(r.uri()).get("azimuth"))
                .containsExactly("30/-90", "10/90");
        var nine = steps.stream().filter(s -> s.end().equals(Instant.parse("2026-10-06T07:00:00Z"))).findFirst().orElseThrow();
        // the recording is replayed for both
        assertThat(nine.powerKw()).isCloseTo(OpenMeteo.powerKw(156.6, 3.0, 0.14) + OpenMeteo.powerKw(156.6, 2.0, 0.0),
                within(1e-9));
        assertThat(steps).hasSize(72);
    }

    @Test
    void aBadServerAddressIsRefusedBeforeAnyArrayIsAsked() {
        var http = ReplayHttp.always(new Reply(200, RECORDED));
        var system = withArrays(new PanelArray("", 3.0, 30, -90, 0.14), new PanelArray("", 2.0, 10, 90, 0.0));
        assertThatThrownBy(() -> new OpenMeteo(http).fetch(system, Map.of("base_url", "http://example.org"), NOW))
                .isInstanceOf(ProviderError.MissingSetting.class);
        assertThat(http.requests).isEmpty();
    }

    @Test
    void itDeclaresAServerAddressAndASecretApiKey() {
        assertThat(OpenMeteo.INFO.settings()).extracting(SettingSpec::name, SettingSpec::secret)
                .containsExactly(tuple("base_url", false),
                        tuple("api_key", true));
    }

    @Test
    void anErrorReportedInAGoodAnswerKeepsItsReason() {
        assertThatThrownBy(() -> fetch("{\"error\": true, \"reason\": \"Tilt must be 0 to 90\"}", Map.of(), EXAMPLE_SYSTEM))
                .isInstanceOf(ProviderError.BadResponse.class)
                .hasMessageStartingWith("Open-Meteo reports an error; ").hasMessageContaining("Tilt must be 0 to 90");
    }
}
