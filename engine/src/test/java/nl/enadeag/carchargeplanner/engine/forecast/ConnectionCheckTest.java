package nl.enadeag.carchargeplanner.engine.forecast;

import static nl.enadeag.carchargeplanner.engine.forecast.FakeProvider.crash;
import static nl.enadeag.carchargeplanner.engine.forecast.FakeProvider.error;
import static nl.enadeag.carchargeplanner.engine.forecast.FakeProvider.steps;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ConnectionCheckTest {

    private static final LocalDate DAY = LocalDate.of(2026, 7, 3);
    private static final java.time.Instant NOW = DAY.atTime(7, 0).atZone(SunnyDays.TZ).toInstant();
    private static final String SECRET = "CHECK-SECRET-8";

    private static ConnectionResult attempt(FakeProvider.Answer answer) {
        return ConnectionCheck.attempt(new FakeProvider("main", "Main", answer), ProviderCase.EXAMPLE_SYSTEM, Map.of(), NOW,
                List.of());
    }

    /** Java 17's compiler no longer checks that describe covers every error; this does. */
    @Test
    void theErrorsAreTheFourTheCheckDescribes() {
        assertThat(ProviderError.class.getPermittedSubclasses())
                .as("a new error needs a branch in ConnectionCheck.describe")
                .containsExactlyInAnyOrder(ProviderError.Unavailable.class, ProviderError.RateLimited.class,
                        ProviderError.BadResponse.class, ProviderError.MissingSetting.class);
    }

    @Test
    void aWorkingProviderSaysHowManyDaysItForecasts() {
        assertThat(attempt(steps(SunnyDays.days(DAY, 4, 3.0)))).isEqualTo(new ConnectionResult(true, 4, "OK: 4 days of forecast"));
        assertThat(attempt(steps(SunnyDays.day(DAY, 3.0)))).isEqualTo(new ConnectionResult(true, 1, "OK: 1 day of forecast"));
    }

    /** Open-Meteo's first step is the hour before midnight: four days touch five dates, and the check said "5 days". */
    @Test
    void theDaysCountedAreTodayOnwardsEvenWhenTheFirstStepBelongsToYesterday() {
        var start = DAY.minusDays(1).atTime(23, 0).atZone(SunnyDays.TZ).toInstant();
        var steps = new ArrayList<ForecastStep>();
        for (int hour = 0; hour < 96; hour++) {
            steps.add(new ForecastStep(start.plus(Duration.ofHours(hour)), start.plus(Duration.ofHours(hour + 1)), 1.0));
        }
        assertThat(ConnectionCheck.forecastDays(steps, NOW, SunnyDays.TZ)).isEqualTo(4);
        assertThat(ConnectionCheck.forecastDays(steps.subList(0, 25), NOW, SunnyDays.TZ)).isEqualTo(1);
        assertThat(ConnectionCheck.forecastDays(List.of(), NOW, SunnyDays.TZ)).isZero();
    }

    static Stream<Arguments> failures() {
        return Stream.of(
                Arguments.of(new ProviderError.RateLimited("busy", Duration.ofSeconds(90)),
                        "Main says there were too many requests. Try again in 2 minutes."),
                Arguments.of(new ProviderError.RateLimited("busy", Duration.ofSeconds(20)),
                        "Main says there were too many requests. Try again in 1 minute."),
                Arguments.of(new ProviderError.RateLimited("busy", null),
                        "Main says there were too many requests. Try again later."),
                Arguments.of(new ProviderError.Unavailable("could not reach Main (ConnectException)"),
                        "Could not get a forecast from Main: could not reach Main (ConnectException)"),
                Arguments.of(new ProviderError.MissingSetting("the server address must start with https://"),
                        "Main is not set up completely: the server address must start with https://"),
                Arguments.of(new ProviderError.BadResponse("odd"), "Main gave an answer the app does not understand: odd"));
    }

    @ParameterizedTest
    @MethodSource("failures")
    void whatWentWrongIsSaidInPlainWords(ProviderError failure, String message) {
        assertThat(attempt(error(failure))).isEqualTo(new ConnectionResult(false, 0, message));
    }

    @Test
    void aBugInTheProviderSaysItsKindButNotWhatItSaid() {
        assertThat(attempt(crash(new IllegalStateException(SECRET))))
                .isEqualTo(new ConnectionResult(false, 0, "Main: unexpected error (IllegalStateException)"));
    }

    @Test
    void aMissingRequiredSettingIsNamedAndNothingIsSent() {
        var needy = new FakeProvider(FakeProvider.info("main", "Main",
                List.of(new SettingSpec("api_key", "API key", "Your key.", false, true))), steps(SunnyDays.day(DAY, 3.0)));

        var result = ConnectionCheck.attempt(needy, ProviderCase.EXAMPLE_SYSTEM, Map.of(), NOW, List.of());

        assertThat(result).isEqualTo(new ConnectionResult(false, 0, "API key is required."));
        assertThat(needy.callCount()).isZero();
    }

    /** A server can echo what it was sent; the key tried and every stored one are hidden. */
    @Test
    void aKeyTheServerEchoesIsNeverInTheMessage() {
        var echoing = new FakeProvider(FakeProvider.info("main", "Main",
                List.of(new SettingSpec("api_key", "API key", "Your key.", false, true))),
                error(new ProviderError.BadResponse("Main says: you sent " + SECRET + ", not STORED-ELSEWHERE-4")));

        var result = ConnectionCheck.attempt(echoing, ProviderCase.EXAMPLE_SYSTEM, Map.of("api_key", SECRET), NOW,
                List.of("STORED-ELSEWHERE-4"));

        assertThat(result.message()).isEqualTo("Main gave an answer the app does not understand: Main says: you sent "
                + "***, not ***");
    }
}
