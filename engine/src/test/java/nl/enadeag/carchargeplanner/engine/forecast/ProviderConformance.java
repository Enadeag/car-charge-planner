package nl.enadeag.carchargeplanner.engine.forecast;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import nl.enadeag.carchargeplanner.LogCapture;
import nl.enadeag.carchargeplanner.engine.forecast.ReplayHttp.Route;
import java.io.IOException;
import java.net.ConnectException;
import java.net.http.HttpTimeoutException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The conformance kit: what every forecast provider must do, run against recorded answers. A provider's test class
 * extends it and describes the provider in {@link #providerCase()}; nothing here touches the network.
 */
public abstract class ProviderConformance {

    /** Where the forecast cache of {@link #addressesCoordinatesAndSecretsNeverReachLogsOrReasons()} goes. */
    @TempDir
    Path cacheFolder;

    protected abstract ProviderCase providerCase();

    @Test
    void declaresCompleteMetadata() {
        var info = providerCase().provider(providerCase().ok()).provider().info();
        assertThat(List.of(info.name(), info.description(), info.attribution(), info.sends())).allMatch(t -> !t.isBlank());
        assertThat(info.termsUrl()).startsWith("https://");
        assertThat(info.minRefresh()).isPositive();
        assertThat(info.horizon()).isPositive();
        assertThat(info.maxUsefulAge()).as("useful for at least the refresh interval").isGreaterThanOrEqualTo(info.minRefresh());
        assertThat(info.settings()).extracting(SettingSpec::name).doesNotHaveDuplicates();
        assertThat(info.settings()).allMatch(s -> !s.name().isBlank() && !s.label().isBlank() && !s.hint().isBlank());
    }

    @Test
    void aGoodAnswerGivesValidStepsThroughItsHttpGet() throws ProviderError {
        var c = providerCase();
        var made = c.provider(c.ok());
        var steps = made.provider().fetch(ProviderCase.EXAMPLE_SYSTEM, c.allSettings(), c.now());
        assertThat(steps).isNotEmpty();
        assertThat(StepRules.problems(steps)).isEmpty();
        assertThat(made.http().requests).as("asked through the HttpGet it was given").isNotEmpty();
        assertThat(made.http().unexpected).isEmpty();
        // a day of slack: a provider may give the rest of the last day
        var latest = c.now().plus(made.provider().info().horizon()).plus(java.time.Duration.ofDays(1));
        assertThat(steps.get(steps.size() - 1).end()).as("within the declared horizon").isBeforeOrEqualTo(latest);
    }

    /**
     * Two panel arrays give one valid forecast: the sum of the two single-array forecasts when the provider asks once
     * per array (the recording is replayed for each), and a failure for the second array fails the whole forecast.
     */
    @Test
    void severalPanelArraysGiveOneForecastOfTheirSum() throws ProviderError {
        var c = providerCase();
        var arrays = List.of(ProviderCase.EXAMPLE_SYSTEM.arrays().get(0), ProviderCase.SECOND_ARRAY);
        var system = ProviderCase.EXAMPLE_SYSTEM.withArrays(arrays);
        var made = c.provider(c.ok());
        var total = made.provider().fetch(system, c.allSettings(), c.now());
        assertThat(total).isNotEmpty();
        assertThat(StepRules.problems(total)).isEmpty();
        assumeTrue(made.http().requests.size() == arrays.size(), "one request for all arrays: the sum is unknown");

        var singles = new ArrayList<List<ForecastStep>>();
        for (var array : arrays) {
            singles.add(c.provider(c.ok()).provider().fetch(system.withArrays(List.of(array)), c.allSettings(), c.now()));
        }
        var expected = ArraySum.addUp(singles);
        assertThat(total).hasSameSizeAs(expected);
        for (int i = 0; i < total.size(); i++) {
            assertThat(total.get(i).start()).isEqualTo(expected.get(i).start());
            assertThat(total.get(i).powerKw()).isCloseTo(expected.get(i).powerKw(), within(1e-9));
        }
        var counter = new AtomicInteger();
        var secondFails = new ArrayList<Route>();
        secondFails.add(new Route(ignored -> counter.getAndIncrement() >= 1, new HttpGet.Reply(503, "Unavailable"),
                null));
        secondFails.addAll(c.ok());
        var failing = c.provider(secondFails).provider();
        assertThatThrownBy(() -> failing.fetch(system, c.allSettings(), c.now())).isInstanceOf(ProviderError.Unavailable.class);
    }

    @Test
    void theSameAnswerGivesTheSameSteps() throws ProviderError {
        var c = providerCase();
        var provider = c.provider(c.ok()).provider();
        assertThat(provider.fetch(ProviderCase.EXAMPLE_SYSTEM, c.allSettings(), c.now()))
                .isEqualTo(provider.fetch(ProviderCase.EXAMPLE_SYSTEM, c.allSettings(), c.now()));
    }

    @Test
    void itLeavesItsSettingsAlone() throws ProviderError {
        var c = providerCase();
        var settings = new HashMap<>(c.allSettings());
        c.provider(c.ok()).provider().fetch(ProviderCase.EXAMPLE_SYSTEM, settings, c.now());
        assertThat(settings).isEqualTo(c.allSettings());
    }

    @Test
    void aRateLimitIsRateLimitedWithTheRetryTime() {
        var c = providerCase();
        assumeTrue(c.rateLimited() != null, "this provider has no rate-limit answer");
        var error = (ProviderError.RateLimited) fails(c.rateLimited(), ProviderError.RateLimited.class);
        assertThat(error.retryAfter().orElse(null)).isEqualTo(c.retryAfter());
    }

    @Test
    void aServerErrorIsUnavailable() {
        fails(providerCase().serverError(), ProviderError.Unavailable.class);
    }

    static Stream<IOException> noNetwork() {
        return Stream.of(new ConnectException("unreachable"), new HttpTimeoutException("slow"), new IOException("reset"),
                new HttpGet.TooLarge(HttpGet.DEFAULT_MAX_BODY));
    }

    @Test
    void itReadsNoMoreThanAForecastAnswerMayHold() throws ProviderError {
        var c = providerCase();
        var made = c.provider(c.ok());
        made.provider().fetch(ProviderCase.EXAMPLE_SYSTEM, c.allSettings(), c.now());
        assertThat(made.http().requests).allSatisfy(request -> {
            assertThat(request.maxBody().bytes()).isLessThanOrEqualTo(HttpGet.DEFAULT_MAX_BODY.bytes());
            assertThat(request.timeout()).isLessThanOrEqualTo(HttpGet.DEFAULT_TIMEOUT);
        });
    }

    /**
     * What a server writes in an error answer reaches the owner and the log as "<provider> says: ...": without the key
     * it was sent (also when cut short right in it), without control or direction characters, and no longer than
     * {@link ProviderText#MAX_LENGTH}.
     */
    @Test
    void theServersOwnWordsAreQuotedCleanedShortAndWithoutTheKey() {
        var c = providerCase();
        var name = c.provider(c.ok()).provider().info().name();
        assumeFalse(c.secrets().isEmpty(), "this provider has no secret settings");
        var secret = c.secrets().values().iterator().next();
        var prefix = "You sent \u202Ekey\u0007 "; // "You sent key " once cleaned: 13 characters
        // the key straddles the point where the text is cut
        var words = prefix + "x".repeat(ProviderText.MAX_LENGTH - 13 - 3) + secret + " and much more\n".repeat(50);

        var error = fails(c.errorSaying().apply(words), ProviderError.class);

        var message = error.getMessage();
        assertThat(message).contains(name + " says: You sent key ");
        assertThat(message).doesNotContain(secret).doesNotContain(secret.substring(0, secret.length() / 2));
        assertThat(message).doesNotContainPattern("[\\p{Cc}\\p{Cf}]");
        var quoted = message.substring(message.indexOf(name + " says: ") + (name + " says: ").length());
        assertThat(quoted.codePointCount(0, quoted.length())).isLessThanOrEqualTo(ProviderText.MAX_LENGTH + 1);
    }

    /** A server could have been sent, or learned, another provider's key: no stored key reaches a reason or a log. */
    @Test
    void noStoredKeyOfAnyProviderReachesAReasonOrTheLog() {
        var c = providerCase();
        var otherKey = "OtherProviderKey31";
        var made = c.provider(c.errorSaying().apply("we also know " + otherKey));
        var cache = new ForecastCache(cacheFolder);
        try (var log = LogCapture.of("nl.enadeag.carchargeplanner")) {
            var outcome = new ProviderAttempt(made.provider(), cache, new HashMap<>(), new ProviderHealth(), c.now(), true)
                    .forecastOrReason(ProviderCase.EXAMPLE_SYSTEM, c::allSettings,
                            cache.key(ProviderCase.EXAMPLE_SYSTEM, Map.of()), () -> List.of(otherKey));
            var check = ConnectionCheck.attempt(made.provider(), ProviderCase.EXAMPLE_SYSTEM, c.allSettings(), c.now(),
                    List.of(otherKey));

            assertThat(outcome).isInstanceOfSatisfying(ProviderOutcome.Failure.class,
                    failure -> assertThat(failure.reason()).contains("we also know ***").doesNotContain(otherKey));
            assertThat(check.message()).contains("we also know ***").doesNotContain(otherKey);
            assertThat(log.lines()).isNotEmpty().noneMatch(line -> line.contains(otherKey));
        }
    }

    @ParameterizedTest
    @MethodSource("noNetwork")
    void noNetworkIsUnavailable(IOException failure) {
        fails(List.of(Route.failure(failure)), ProviderError.Unavailable.class);
    }

    @Test
    void anAnswerThatIsNotAsDocumentedIsABadResponse() {
        fails(providerCase().badBody(), ProviderError.BadResponse.class);
    }

    @Test
    void anEmptyAnswerNeverCrashes() {
        var c = providerCase();
        var made = c.provider(c.emptyBody());
        try {
            assertThat(StepRules.problems(made.provider().fetch(ProviderCase.EXAMPLE_SYSTEM, c.allSettings(), c.now()))).isEmpty();
        } catch (ProviderError ignored) {
            // a provider error is a fine answer to nothing
        }
        assertThat(made.http().unexpected).isEmpty();
    }

    @Test
    void missingRequiredSettingsAreReportedBeforeAnythingIsSent() {
        var c = providerCase();
        var made = c.provider(c.ok());
        var required = made.provider().info().settings().stream().filter(s -> !s.optional()).map(SettingSpec::name).toList();
        assumeFalse(required.isEmpty(), "this provider has no required settings");
        for (var key : required) {
            var settings = new HashMap<>(c.allSettings());
            settings.remove(key);
            assertThatThrownBy(() -> made.provider().fetch(ProviderCase.EXAMPLE_SYSTEM, settings, c.now()))
                    .isInstanceOf(ProviderError.MissingSetting.class);
        }
        assertThat(made.http().requests).isEmpty();
    }

    /**
     * A config file or keychain edited by hand can hold what Settings would refuse: a line break in a value must not
     * reach a request, where it could end a header or the address and start one of its own.
     */
    @Test
    void aSettingWithALineBreakIsRefusedOrNeverReachesARequest() {
        var c = providerCase();
        var wrong = "ftp://x y/?a=1&b=2#\r\nX-Injected: 1";
        for (var spec : c.provider(c.ok()).provider().info().settings()) {
            assertThat(spec.format().accepts(wrong)).as("%s refuses the value", spec.name()).isFalse();
            var made = c.provider(c.ok());
            var settings = new HashMap<>(c.allSettings());
            settings.put(spec.name(), wrong);
            var thrown = catchThrowable(() -> made.provider().fetch(ProviderCase.EXAMPLE_SYSTEM, settings, c.now()));
            if (thrown instanceof ProviderError.MissingSetting) {
                assertThat(made.http().requests).as("%s: nothing sent", spec.name()).isEmpty();
            }
            assertThat(made.http().requests).allSatisfy(request -> {
                assertThat(request.uri().toString()).as("%s: address", spec.name()).doesNotContainPattern("[\\r\\n]");
                assertThat(request.headers().values()).as("%s: headers", spec.name())
                        .noneMatch(value -> value.contains("\r") || value.contains("\n"));
            });
        }
    }

    @Test
    void addressesCoordinatesAndSecretsNeverReachLogsOrReasons() {
        var c = providerCase();
        var shown = new ArrayList<String>();
        var addresses = new ArrayList<String>();
        try (var log = LogCapture.of("nl.enadeag.carchargeplanner")) {
            for (var routes : scenarios(c)) {
                var made = c.provider(routes);
                var cache = new ForecastCache(cacheFolder);
                // as the forecast service asks, so the lines it logs about a provider failing are made too
                var outcome = new ProviderAttempt(made.provider(), cache, new HashMap<>(), new ProviderHealth(), c.now(),
                        true).forecastOrReason(ProviderCase.EXAMPLE_SYSTEM, c::allSettings,
                        cache.key(ProviderCase.EXAMPLE_SYSTEM, Map.of()), List::of);
                if (outcome instanceof ProviderOutcome.Failure failure) {
                    shown.add(failure.reason());
                }
                made.http().requests.forEach(request -> {
                    addresses.add(request.uri().toString());
                    addresses.add(request.uri().getRawQuery());
                });
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
        var coordinate = Pattern.compile("(?<![\\d.])(52\\.37|4\\.90?)(?![\\d])");
        for (var text : shown) {
            assertThat(addresses).as("an address in: %s", text).noneMatch(address -> address != null
                    && text.contains(address));
            assertThat(coordinate.matcher(text).find()).as("a coordinate in: %s", text).isFalse();
            c.secrets().values().forEach(secret -> assertThat(text).doesNotContain(secret));
        }
    }

    private static List<List<Route>> scenarios(ProviderCase c) {
        var echo = new StringBuilder("you sent");
        c.secrets().values().forEach(secret -> echo.append(' ').append(secret));
        var scenarios = new ArrayList<>(List.of(c.ok(), c.serverError(), c.badBody(), c.emptyBody(),
                List.of(Route.failure(new ConnectException("down"))), c.errorSaying().apply(echo.toString())));
        if (c.rateLimited() != null) {
            scenarios.add(c.rateLimited());
        }
        return scenarios;
    }

    private ProviderError fails(List<Route> routes, Class<? extends ProviderError> kind) {
        var c = providerCase();
        var made = c.provider(routes);
        var error = catchThrowable(() -> made.provider().fetch(ProviderCase.EXAMPLE_SYSTEM, c.allSettings(), c.now()));
        assertThat(error).isInstanceOf(kind);
        assertThat(made.http().unexpected).isEmpty();
        return (ProviderError) error;
    }

    @ParameterizedTest
    @CsvSource({"500, true", "499, false"})
    void aStatusFrom500OnIsTheServersProblemAndBelowItARefusal(int status, boolean serverProblem) {
        fails(List.of(Route.answer("", new HttpGet.Reply(status, ""))),
                serverProblem ? ProviderError.Unavailable.class : ProviderError.BadResponse.class);
    }
}
