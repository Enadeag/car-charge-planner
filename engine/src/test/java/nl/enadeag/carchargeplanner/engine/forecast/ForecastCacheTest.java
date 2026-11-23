package nl.enadeag.carchargeplanner.engine.forecast;

import static org.assertj.core.api.Assertions.assertThat;

import nl.enadeag.carchargeplanner.engine.config.ProviderId;
import nl.enadeag.carchargeplanner.engine.store.FilePrivacy;
import nl.enadeag.carchargeplanner.engine.store.PrivateFiles;
import nl.enadeag.carchargeplanner.engine.trip.Trip;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;

/** The moments the cache holds, read back by a cache that starts afresh, as after a restart. */
class ForecastCacheTest {

    private static final PrivateFiles FILES = new PrivateFiles(FilePrivacy.NONE);
    private static final ProviderId MAIN = new ProviderId("main");
    private static final Instant NOW = Instant.parse("2026-07-03T05:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final Duration DAY = ProviderError.RateLimited.MAX_RETRY_AFTER;

    @TempDir
    Path folder;

    private ForecastCache cache() {
        return new ForecastCache(folder, FILES, CLOCK);
    }

    private JsonNode key() {
        return cache().key(ProviderCase.EXAMPLE_SYSTEM, Map.of());
    }

    private static List<ForecastStep> hourAt(Instant start) {
        return List.of(new ForecastStep(start, start.plus(Duration.ofHours(1)), 2.5));
    }

    static Stream<Arguments> theRangeOfMoments() {
        return Stream.of(Arguments.of(Trip.EARLIEST_INSTANT, true),
                Arguments.of(Trip.EARLIEST_INSTANT.minusNanos(1), false),
                Arguments.of(Trip.INSTANTS_END.minusNanos(1), true),
                Arguments.of(Trip.INSTANTS_END, false));
    }

    @ParameterizedTest
    @MethodSource("theRangeOfMoments")
    void aForecastIsUsedOnlyWhenItWasFetchedWithinTheRangeOfMoments(Instant fetchedAt, boolean used) {
        cache().saveForecast(MAIN, key(), fetchedAt, hourAt(NOW));

        assertThat(cache().forecast(MAIN, key(), Trip.INSTANTS_END)).isEqualTo(
                used ? Optional.of(new ForecastCache.Cached(fetchedAt, hourAt(NOW))) : Optional.empty());
    }

    @ParameterizedTest
    @MethodSource("theRangeOfMoments")
    void aCoolDownCountsOnlyWhenItEndsWithinTheRangeOfMoments(Instant until, boolean counts) {
        cache().saveCooldown(MAIN, until);

        assertThat(cache().cooldownUntil(MAIN, until.minus(Duration.ofHours(1))))
                .isEqualTo(counts ? Optional.of(until) : Optional.empty());
    }

    @Test
    void aForecastWhoseStepsBreakTheRulesIsNotUsed() {
        cache().saveForecast(MAIN, key(), NOW, hourAt(Trip.EARLIEST_INSTANT.minusNanos(1)));
        assertThat(cache().forecast(MAIN, key(), NOW)).isEmpty();

        var tooLong = List.of(new ForecastStep(NOW, NOW.plus(StepRules.MAX_SPAN).plusNanos(1), 2.5));
        cache().saveForecast(MAIN, key(), NOW, tooLong);
        assertThat(cache().forecast(MAIN, key(), NOW)).isEmpty();
    }

    @Test
    void aCoolDownOfADayIsKept() {
        cache().saveCooldown(MAIN, NOW.plus(DAY));

        assertThat(cache().cooldownUntil(MAIN, NOW)).contains(NOW.plus(DAY));
    }

    @Test
    void aLaterCoolDownIsCutToADayFromNowAndKeptSo() {
        cache().saveCooldown(MAIN, NOW.plus(DAY).plusNanos(1));

        assertThat(cache().cooldownUntil(MAIN, NOW)).contains(NOW.plus(DAY));
        assertThat(cache().cooldownUntil(MAIN, NOW.plus(Duration.ofHours(1)))).contains(NOW.plus(DAY));
    }

    @ParameterizedTest
    @MethodSource("clocksNearTheEndsOfTime")
    void aCoolDownIsReadWithoutFailingAtTheEndsOfWhatAnInstantHolds(Instant clock, Optional<Instant> expected) {
        cache().saveCooldown(MAIN, NOW.plus(DAY));

        assertThat(cache().cooldownUntil(MAIN, clock)).isEqualTo(expected);
    }

    static Stream<Arguments> clocksNearTheEndsOfTime() {
        return Stream.of(Arguments.of(Instant.MAX, Optional.of(NOW.plus(DAY))),
                Arguments.of(Instant.MAX.minus(DAY), Optional.of(NOW.plus(DAY))),
                Arguments.of(Instant.MIN, Optional.of(Instant.MIN.plus(DAY))));
    }

    @Test
    void cuttingACoolDownKeepsTheForecastItsKeyAndTheFailedRefresh() {
        cache().saveForecast(MAIN, key(), NOW, hourAt(NOW));
        cache().saveCooldown(MAIN, NOW.plus(DAY).plusNanos(1));

        assertThat(cache().cooldownUntil(MAIN, NOW)).contains(NOW.plus(DAY));

        var afresh = cache();
        assertThat(afresh.forecast(MAIN, key(), NOW)).contains(new ForecastCache.Cached(NOW, hourAt(NOW)));
        assertThat(afresh.refreshFailed(MAIN)).isTrue();
    }
}
