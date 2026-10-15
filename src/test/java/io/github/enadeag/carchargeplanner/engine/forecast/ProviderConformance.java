package io.github.enadeag.carchargeplanner.engine.forecast;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.github.enadeag.carchargeplanner.engine.forecast.ReplayHttp.Route;
import java.io.IOException;
import java.net.ConnectException;
import java.net.http.HttpTimeoutException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The conformance kit: what every forecast provider must do, run against recorded answers. A provider's test class
 * extends it and describes the provider in {@link #providerCase()}; nothing here touches the network.
 */
public abstract class ProviderConformance {

    protected abstract ProviderCase providerCase();

    @Test
    void declaresCompleteMetadata() {
        var info = providerCase().provider(providerCase().ok()).provider().info();
        assertThat(info.id()).matches("[a-z0-9][a-z0-9-]*");
        assertThat(List.of(info.name(), info.description(), info.attribution(), info.sends())).allMatch(t -> !t.isBlank());
        assertThat(info.termsUrl()).startsWith("https://");
        assertThat(info.minRefresh()).isPositive();
        assertThat(info.horizon()).isPositive();
        assertThat(info.maxUsefulAge()).as("useful for at least the refresh interval").isGreaterThanOrEqualTo(info.minRefresh());
        assertThat(info.settings()).extracting(SettingSpec::key).doesNotHaveDuplicates();
        assertThat(info.settings()).allMatch(s -> !s.key().isBlank() && !s.label().isBlank() && !s.hint().isBlank());
    }

    @Test
    void aGoodAnswerGivesValidStepsThroughItsHttpGet() throws ProviderError {
        var c = providerCase();
        var made = c.provider(c.ok());
        var steps = made.provider().fetch(ProviderCase.EXAMPLE_SYSTEM, c.allSettings(), c.now());
        assertThat(steps).isNotEmpty();
        assertThat(Steps.problems(steps)).isEmpty();
        assertThat(made.http().requests).as("asked through the HttpGet it was given").isNotEmpty();
        assertThat(made.http().unexpected).isEmpty();
        // a day of slack: a provider may give the rest of the last day
        var latest = c.now().plus(made.provider().info().horizon()).plus(java.time.Duration.ofDays(1));
        assertThat(steps.getLast().end()).as("within the declared horizon").isBeforeOrEqualTo(latest);
    }

    /**
     * Two panel arrays give one valid forecast: the sum of the two single-array forecasts when the provider asks once
     * per array (the recording is replayed for each), and a failure for the second array fails the whole forecast.
     */
    @Test
    void severalPanelArraysGiveOneForecastOfTheirSum() throws ProviderError {
        var c = providerCase();
        var arrays = List.of(ProviderCase.EXAMPLE_SYSTEM.arrays().getFirst(), ProviderCase.SECOND_ARRAY);
        var system = ProviderCase.EXAMPLE_SYSTEM.withArrays(arrays);
        var made = c.provider(c.ok());
        var total = made.provider().fetch(system, c.allSettings(), c.now());
        assertThat(total).isNotEmpty();
        assertThat(Steps.problems(total)).isEmpty();
        assumeTrue(made.http().requests.size() == arrays.size(), "one request for all arrays: the sum is unknown");

        var singles = new ArrayList<List<ForecastStep>>();
        for (var array : arrays) {
            singles.add(c.provider(c.ok()).provider().fetch(system.withArrays(List.of(array)), c.allSettings(), c.now()));
        }
        var expected = Steps.addUp(singles);
        assertThat(total).hasSameSizeAs(expected);
        for (int i = 0; i < total.size(); i++) {
            assertThat(total.get(i).start()).isEqualTo(expected.get(i).start());
            assertThat(total.get(i).powerKw()).isCloseTo(expected.get(i).powerKw(), within(1e-9));
        }
        var counter = new AtomicInteger();
        var secondFails = new ArrayList<Route>();
        secondFails.add(new Route(request -> counter.getAndIncrement() >= 1, new HttpGet.Reply(503, "Unavailable"), null));
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
        return Stream.of(new ConnectException("unreachable"), new HttpTimeoutException("slow"), new IOException("reset"));
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
            assertThat(Steps.problems(made.provider().fetch(ProviderCase.EXAMPLE_SYSTEM, c.allSettings(), c.now()))).isEmpty();
        } catch (ProviderError e) {
            // a provider error is a fine answer to nothing
        }
        assertThat(made.http().unexpected).isEmpty();
    }

    @Test
    void missingRequiredSettingsAreReportedBeforeAnythingIsSent() {
        var c = providerCase();
        var made = c.provider(c.ok());
        var required = made.provider().info().settings().stream().filter(s -> !s.optional()).map(SettingSpec::key).toList();
        assumeFalse(required.isEmpty(), "this provider has no required settings");
        for (var key : required) {
            var settings = new HashMap<>(c.allSettings());
            settings.remove(key);
            assertThatThrownBy(() -> made.provider().fetch(ProviderCase.EXAMPLE_SYSTEM, settings, c.now()))
                    .isInstanceOf(ProviderError.NotConfigured.class);
        }
        assertThat(made.http().requests).isEmpty();
    }

    @Test
    void secretsNeverReachLogsOrErrors() {
        var c = providerCase();
        assumeFalse(c.secrets().isEmpty(), "this provider has no secret settings");
        var shown = new ArrayList<String>();
        var capture = new Handler() {
            @Override
            public void publish(LogRecord record) {
                shown.add(record.getMessage());
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        var root = Logger.getLogger("");
        var previous = root.getLevel();
        root.addHandler(capture);
        root.setLevel(Level.ALL);
        try {
            for (var routes : scenarios(c)) {
                try {
                    c.provider(routes).provider().fetch(ProviderCase.EXAMPLE_SYSTEM, c.allSettings(), c.now());
                } catch (ProviderError e) {
                    shown.add(e.getMessage());
                }
            }
        } finally {
            root.removeHandler(capture);
            root.setLevel(previous);
        }
        c.secrets().values().forEach(secret -> assertThat(shown).noneMatch(text -> text != null && text.contains(secret)));
    }

    private static List<List<Route>> scenarios(ProviderCase c) {
        var scenarios = new ArrayList<>(List.of(c.ok(), c.serverError(), c.badBody(), c.emptyBody(),
                List.of(Route.failure(new ConnectException("down")))));
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
}
