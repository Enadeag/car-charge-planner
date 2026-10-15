package io.github.enadeag.carchargeplanner.engine.forecast;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import io.github.enadeag.carchargeplanner.engine.config.Car;
import io.github.enadeag.carchargeplanner.engine.config.Charger;
import io.github.enadeag.carchargeplanner.engine.config.HomeSystem;
import io.github.enadeag.carchargeplanner.engine.config.House;
import io.github.enadeag.carchargeplanner.engine.config.PanelArray;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class OpenMeteoTest {

    private static final PanelArray SOUTH_EAST = new PanelArray("", 4.8, 35, -45, 0.14);
    private static final PanelArray WEST = new PanelArray("Garage", 2.0, 20, 90, 0.10);

    /** Answers every request with the same reply, and keeps the addresses asked for. */
    private static final class ReplayHttp implements HttpGet {
        final List<URI> asked = new ArrayList<>();
        private final HttpReply reply;

        ReplayHttp(int status, String body) {
            reply = new HttpReply(status, body);
        }

        @Override
        public HttpReply get(URI uri) {
            asked.add(uri);
            return reply;
        }
    }

    private static String recorded() {
        try (var in = OpenMeteoTest.class.getResourceAsStream("open_meteo_3days.json")) {
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static HomeSystem system(PanelArray... arrays) {
        return new HomeSystem(51.0, 5.0, ZoneId.of("Europe/Brussels"), List.of(arrays), new House(0.5),
                new Car(60, 18, 40), new Charger(1, 6, 16, 230, 0.9));
    }

    @Test
    void theValueStampedAtNineCoversTheHourBeforeItAndBecomesPower() throws ForecastUnavailable {
        var steps = new OpenMeteo(new ReplayHttp(200, recorded())).forecast(system(SOUTH_EAST));

        assertThat(steps).hasSize(72).allSatisfy(s -> assertThat(Duration.between(s.start(), s.end())).hasHours(1));
        // 09:00 summer time in Brussels; the recording has 156.6 W/m2 there
        var nine = steps.stream().filter(s -> s.end().equals(Instant.parse("2026-10-06T07:00:00Z"))).findFirst().orElseThrow();
        assertThat(nine.start()).isEqualTo(Instant.parse("2026-10-06T06:00:00Z"));
        assertThat(nine.powerKw()).isCloseTo(4.8 * 0.1566 * 0.86, within(1e-9));
    }

    @Test
    void theRequestDescribesTheLocationAndThePanels() throws ForecastUnavailable {
        var http = new ReplayHttp(200, recorded());
        new OpenMeteo(http).forecast(system(SOUTH_EAST));

        assertThat(http.asked).singleElement().satisfies(uri -> {
            assertThat(uri.getHost()).isEqualTo("api.open-meteo.com");
            assertThat(uri.getQuery()).contains("latitude=51.0", "longitude=5.0", "tilt=35.0", "azimuth=-45.0",
                    "timezone=Europe/Brussels", "hourly=global_tilted_irradiance", "timeformat=unixtime");
        });
    }

    @Test
    void eachPanelArrayIsAskedForAndThePowerIsAddedUp() throws ForecastUnavailable {
        var http = new ReplayHttp(200, recorded());
        var both = new OpenMeteo(http).forecast(system(SOUTH_EAST, WEST));
        var one = new OpenMeteo(new ReplayHttp(200, recorded())).forecast(system(SOUTH_EAST));

        assertThat(http.asked).hasSize(2);
        assertThat(http.asked.get(1).getQuery()).contains("tilt=20.0", "azimuth=90.0");
        // the same recording for both, so the second array adds its own size and losses to each hour
        for (int i = 0; i < one.size(); i++) {
            double westShare = 2.0 * 0.90 / (4.8 * 0.86);
            assertThat(both.get(i).powerKw()).isCloseTo(one.get(i).powerKw() * (1 + westShare), within(1e-9));
        }
    }

    @Test
    void aNullAtTheEdgeOfTheHorizonIsNoPower() throws ForecastUnavailable {
        var body = "{\"hourly\": {\"time\": [1791237600], \"global_tilted_irradiance\": [null]}}";
        var steps = new OpenMeteo(new ReplayHttp(200, body)).forecast(system(SOUTH_EAST));
        assertThat(steps).singleElement().extracting(ForecastStep::powerKw).isEqualTo(0.0);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "<html>maintenance</html>",
        "{}",
        "{\"hourly\": {\"time\": [1791237600, 1791241200], \"global_tilted_irradiance\": [1.0]}}",
        "{\"hourly\": {\"time\": [\"soon\"], \"global_tilted_irradiance\": [1.0]}}",
        "{\"hourly\": {\"time\": [1791237600], \"global_tilted_irradiance\": [\"lots\"]}}",
        "{\"hourly\": {\"time\": [1791241200, 1791237600], \"global_tilted_irradiance\": [1.0, 1.0]}}",
        "{\"hourly\": {\"time\": [1791237600], \"global_tilted_irradiance\": [-50.0]}}",
        "{\"hourly\": {\"time\": [1000000000000000000000000000000], \"global_tilted_irradiance\": [1.0]}}",
    })
    void anAnswerThatIsNotAsDocumentedGivesNoForecast(String body) {
        assertThatThrownBy(() -> new OpenMeteo(new ReplayHttp(200, body)).forecast(system(SOUTH_EAST)))
                .isInstanceOf(ForecastUnavailable.class)
                .hasMessageContaining("not what it documents");
    }

    @Test
    void anErrorStatusGivesNoForecastAndSaysWhich() {
        assertThatThrownBy(() -> new OpenMeteo(new ReplayHttp(503, "busy")).forecast(system(SOUTH_EAST)))
                .isInstanceOf(ForecastUnavailable.class)
                .hasMessageContaining("503");
    }

    @Test
    void noNetworkGivesNoForecast() {
        HttpGet offline = uri -> {
            throw new java.net.ConnectException("no route");
        };
        assertThatThrownBy(() -> new OpenMeteo(offline).forecast(system(SOUTH_EAST)))
                .isInstanceOf(ForecastUnavailable.class)
                .hasMessageContaining("Could not reach Open-Meteo");
    }
}
