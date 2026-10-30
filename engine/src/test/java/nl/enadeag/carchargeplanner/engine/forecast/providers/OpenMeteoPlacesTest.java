package nl.enadeag.carchargeplanner.engine.forecast.providers;

import nl.enadeag.carchargeplanner.engine.forecast.HttpGet;
import nl.enadeag.carchargeplanner.engine.forecast.ByteSize;
import static org.assertj.core.api.Assertions.assertThat;

import nl.enadeag.carchargeplanner.engine.config.Coordinates;
import nl.enadeag.carchargeplanner.engine.forecast.HttpGet.Reply;
import nl.enadeag.carchargeplanner.engine.forecast.Place;
import nl.enadeag.carchargeplanner.engine.forecast.PlaceResults;
import nl.enadeag.carchargeplanner.engine.forecast.PlaceResults.Problem;
import nl.enadeag.carchargeplanner.engine.forecast.Recorded;
import nl.enadeag.carchargeplanner.engine.forecast.ReplayHttp;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URLDecoder;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class OpenMeteoPlacesTest {

    private static final String AMSTERDAM = Recorded.answer("open_meteo_geocoding_amsterdam.json");
    private static final String NOTHING = Recorded.answer("open_meteo_geocoding_none.json");

    private ReplayHttp http = ReplayHttp.always(new Reply(200, AMSTERDAM));

    private PlaceResults search(String text, String language, Reply reply) {
        http = ReplayHttp.always(reply);
        return new OpenMeteoPlaces(http).search(text, language);
    }

    private List<Place> found(String body) {
        return ((PlaceResults.Found) search("Amsterdam", "en", new Reply(200, body))).places();
    }

    private Map<String, String> query() {
        return Arrays.stream(http.requests.get(0).uri().getRawQuery().split("&")).map(pair -> pair.split("=", 2))
                .collect(Collectors.toMap(pair -> pair[0], pair -> URLDecoder.decode(pair[1], StandardCharsets.UTF_8)));
    }

    @Test
    void theRecordedAnswerGivesPlacesInTheOrderTheServiceRanksThem() {
        var places = found(AMSTERDAM);

        assertThat(places).hasSize(8);
        assertThat(places.get(0).label()).isEqualTo("Amsterdam, North Holland, The Netherlands");
        assertThat(places).first().extracting(Place::location, Place::timezone)
                .containsExactly(new Coordinates(52.37403, 4.88969), ZoneId.of("Europe/Amsterdam"));
        assertThat(places.get(4).label()).isEqualTo("eMvelo, Mpumalanga, South Africa");
        assertThat(http.requests).allMatch(r -> r.timeout().compareTo(Duration.ofSeconds(10)) <= 0);
    }

    /** A person waits at the search box for a few kilobytes; a server that sends more is not read on. */
    @Test
    void anAnswerLargerThanAPlaceSearchNeedsIsRefusedAndTheServiceCountsAsUnavailable() {
        var tooLarge = new ReplayHttp(List.of(ReplayHttp.Route.failure(new HttpGet.TooLarge(ByteSize.ofKilobytes(256)))));

        assertThat(new OpenMeteoPlaces(tooLarge).search("Amsterdam", "en"))
                .isEqualTo(new PlaceResults.Failed(PlaceResults.Problem.UNAVAILABLE));
        assertThat(tooLarge.requests.get(0).maxBody()).isEqualTo(ByteSize.ofKilobytes(256));
    }

    @Test
    void twoPlacesThatReadAlikeCanBeToldApartByTheirFinerArea() {
        var ohio = found(AMSTERDAM).stream().filter(p -> p.region().equals("Ohio")).toList();

        assertThat(ohio).hasSize(2);
        assertThat(ohio.get(0).label()).isEqualTo(ohio.get(1).label());
        assertThat(ohio).extracting(Place::detailedLabel).containsExactlyInAnyOrder(
                "Amsterdam, Jefferson, Ohio, United States", "Amsterdam, Licking, Ohio, United States");
    }

    @Test
    void aSearchThatMatchesNothingHasNoPlacesAndNoProblem() {
        assertThat(search("Zzzzzzzzqx", "en", new Reply(200, NOTHING))).isEqualTo(new PlaceResults.Found(List.of()));
    }

    @Test
    void theRequestNamesTheTextTheCountTheLanguageAndAShortTimeout() {
        search("  New   York ", "nl_NL", new Reply(200, AMSTERDAM));
        var uri = http.requests.get(0).uri();

        assertThat(uri.getScheme() + "://" + uri.getHost() + uri.getPath())
                .isEqualTo("https://geocoding-api.open-meteo.com/v1/search");
        assertThat(query()).isEqualTo(Map.of("name", "New York", "count", "8", "language", "nl", "format", "json"));
        assertThat(http.requests.get(0).timeout()).isEqualTo(Duration.ofSeconds(6));
    }

    @Test
    void aMissingLanguageMeansEnglish() {
        search("Amsterdam", "", new Reply(200, AMSTERDAM));
        assertThat(query()).containsEntry("language", "en");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "a", "  x  "})
    void fewerThanTwoCharactersIsNotSearched(String text) {
        assertThat(search(text, "en", new Reply(200, AMSTERDAM))).isEqualTo(new PlaceResults.Failed(Problem.TOO_SHORT));
        assertThat(http.requests).isEmpty();
    }

    @Test
    void twoCharactersAreSearched() {
        search("Ur", "en", new Reply(200, NOTHING));
        assertThat(http.requests).hasSize(1);
    }

    @Test
    void resultsWithoutWhatALocationNeedsAreLeftOut() {
        var rows = """
                {"results": [
                  {"name": "Good", "latitude": 1.0, "longitude": 2.0, "timezone": "Europe/Amsterdam", "admin1": "Region", "country": "Land"},
                  {"name": "No zone", "latitude": 1.0, "longitude": 2.0},
                  {"name": "Odd zone", "latitude": 1.0, "longitude": 2.0, "timezone": "Mars/Olympus"},
                  {"name": "No latitude", "longitude": 2.0, "timezone": "Europe/Amsterdam"},
                  {"name": "Off the map", "latitude": 95.0, "longitude": 2.0, "timezone": "Europe/Amsterdam"},
                  {"name": "", "latitude": 1.0, "longitude": 2.0, "timezone": "Europe/Amsterdam"},
                  {"name": "Text coordinates", "latitude": "north", "longitude": 2.0, "timezone": "Europe/Amsterdam"},
                  "not even a row"
                ]}""";
        assertThat(found(rows)).extracting(Place::name).containsExactly("Good");
    }

    @Test
    void theCountryCodeStandsInForAMissingCountryName() {
        var row = "{\"results\": [{\"name\": \"Place\", \"latitude\": 1.0, \"longitude\": 2.0, \"timezone\": \"UTC\", "
                + "\"country_code\": \"NL\"}]}";
        assertThat(found(row).get(0).label()).isEqualTo("Place, NL");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "400 | {\"error\": true, \"reason\": \"Parameter count must be between 1 and 100.\"} | BAD_RESPONSE",
        "200 | {\"error\": true, \"reason\": \"odd\"} | BAD_RESPONSE",
        "200 | [1, 2] | BAD_RESPONSE",
        "200 | {\"results\": 5} | BAD_RESPONSE",
        "200 | nope | BAD_RESPONSE",
        "200 | <html>maintenance</html> | BAD_RESPONSE",
        "500 | '' | UNAVAILABLE",
        "503 | '' | UNAVAILABLE",
        "429 | '' | RATE_LIMITED",
    })
    void eachFailureIsATypedResultAndNeverAnException(int status, String body, Problem problem) {
        assertThat(search("Amsterdam", "en", new Reply(status, body)))
                .isEqualTo(new PlaceResults.Failed(problem, Optional.empty()));
    }

    @Test
    void aRateLimitGivesTheRetryTimeWhenTheServiceGivesOne() {
        assertThat(search("Amsterdam", "en", new Reply(429, "{}", Map.of("Retry-After", "60"))))
                .isEqualTo(new PlaceResults.Failed(Problem.RATE_LIMITED, Optional.of(Duration.ofSeconds(60))));
    }

    @ParameterizedTest
    @ValueSource(classes = {ConnectException.class, HttpTimeoutException.class})
    void noNetworkIsUnavailable(Class<? extends IOException> kind) throws ReflectiveOperationException {
        http = new ReplayHttp(List.of(ReplayHttp.Route.failure(kind.getConstructor(String.class).newInstance("down"))));
        assertThat(new OpenMeteoPlaces(http).search("Amsterdam", "en")).isEqualTo(new PlaceResults.Failed(Problem.UNAVAILABLE));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "Berlin | Berlin | Deutschland | 'Berlin, Stadt' | 'Berlin, Deutschland' | 'Berlin, Berlin, Stadt, Deutschland'",
        "Paris | Ile-de-France | France | Paris | 'Paris, Ile-de-France, France' | 'Paris, Ile-de-France, France'",
        "Nowhere | '' | '' | '' | Nowhere | Nowhere",
    })
    void theLabelIsNameRegionCountryWithoutRepeatingTheName(String name, String region, String country, String area,
            String label, String detailed) {
        var place = new Place(name, new Coordinates(0, 0), ZoneId.of("UTC"), region, country, area);
        assertThat(place.label()).isEqualTo(label);
        assertThat(place.detailedLabel()).isEqualTo(detailed);
    }

    @Test
    void openMeteoSearchesPlacesThroughItsOwnHttpGetAndForecastSolarHasNoSearch() {
        var results = new OpenMeteo(http).placeSearch().orElseThrow().search("Amsterdam", "en");

        assertThat(((PlaceResults.Found) results).places()).isNotEmpty();
        assertThat(http.requests).hasSize(1);
        assertThat(new ForecastSolar(http).placeSearch()).isEmpty();
    }
}
