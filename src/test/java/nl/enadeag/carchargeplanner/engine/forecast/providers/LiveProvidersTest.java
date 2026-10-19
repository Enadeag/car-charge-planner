package nl.enadeag.carchargeplanner.engine.forecast.providers;

import static nl.enadeag.carchargeplanner.engine.forecast.ProviderCase.EXAMPLE_SYSTEM;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import nl.enadeag.carchargeplanner.engine.forecast.ForecastProvider;
import nl.enadeag.carchargeplanner.engine.forecast.ForecastStep;
import nl.enadeag.carchargeplanner.engine.forecast.HttpGet;
import nl.enadeag.carchargeplanner.engine.forecast.JdkHttpGet;
import nl.enadeag.carchargeplanner.engine.forecast.PlaceResults;
import nl.enadeag.carchargeplanner.engine.forecast.ProviderError;
import java.net.http.HttpClient;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Asks the real services, on their free public plans, whether their answers still read as the recorded ones in the
 * other tests do. Tagged "live": left out of every build, run by hand before a release with {@code ./mvnw test -Plive}
 * (CONTRIBUTING.md, What is tested where). Forecast.Solar's public plan allows 12 requests an hour.
 */
@Tag("live")
class LiveProvidersTest {

    private static HttpClient client;

    @BeforeAll
    static void openTheClient() {
        client = JdkHttpGet.newClient();
    }

    @AfterAll
    static void closeTheClient() {
        client.close();
    }

    static Stream<Arguments> providers() {
        return Stream.of(Arguments.of("Open-Meteo", (Function<HttpGet, ForecastProvider>) OpenMeteo::new),
                Arguments.of("Forecast.Solar", (Function<HttpGet, ForecastProvider>) ForecastSolar::new));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("providers")
    void theLiveForecastReachesTomorrowWithPowersOfTheExampleSystemsSize(String name,
            Function<HttpGet, ForecastProvider> make) throws ProviderError {
        var now = Instant.now();
        var steps = make.apply(new JdkHttpGet(client)).fetch(EXAMPLE_SYSTEM, Map.of(), now);

        var zone = EXAMPLE_SYSTEM.timezone();
        var tomorrow = LocalDate.ofInstant(now, zone).plusDays(1).atStartOfDay(zone).toInstant();
        assertThat(steps.getFirst().start()).isBefore(tomorrow);
        assertThat(steps.getLast().end()).isAfter(tomorrow.plusSeconds(12 * 3600));
        // a wrong unit (W for kW) or a misread field shows as a peak far from the 4.8 kWp of the example's panels
        var kwp = EXAMPLE_SYSTEM.arrays().getFirst().kwp();
        var peak = steps.stream().mapToDouble(ForecastStep::powerKw).max().orElseThrow();
        assertThat(peak).isGreaterThan(0.02 * kwp).isLessThanOrEqualTo(1.2 * kwp);
    }

    @Test
    void theLivePlaceSearchFindsAmsterdamWhereItIs() {
        var results = new OpenMeteoPlaces(new JdkHttpGet(client)).search("Amsterdam", "en");

        assertThat(results).isInstanceOf(PlaceResults.Found.class);
        var first = ((PlaceResults.Found) results).places().getFirst();
        assertThat(first.name()).isEqualTo("Amsterdam");
        assertThat(first.country()).contains("Netherlands");
        assertThat(first.timezone()).isEqualTo(EXAMPLE_SYSTEM.timezone());
        assertThat(first.location().latitude()).isCloseTo(52.37, within(0.05));
        assertThat(first.location().longitude()).isCloseTo(4.89, within(0.05));
    }
}
