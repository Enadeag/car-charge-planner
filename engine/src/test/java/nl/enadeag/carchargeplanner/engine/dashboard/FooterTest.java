package nl.enadeag.carchargeplanner.engine.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import nl.enadeag.carchargeplanner.engine.config.ProviderId;
import nl.enadeag.carchargeplanner.engine.forecast.ForecastSource;
import nl.enadeag.carchargeplanner.engine.forecast.Freshness;
import nl.enadeag.carchargeplanner.engine.forecast.ProviderRole;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class FooterTest {

    private static final String CREDIT = "Weather data: Open-Meteo.com (CC BY 4.0)";
    private static final Instant MADE_AT = Instant.parse("2026-06-01T08:00:00Z");
    private static final Instant FETCHED = MADE_AT.minusSeconds(20 * 60);

    private static ForecastSource source(Freshness freshness, ProviderRole role, String primaryName,
            Optional<Instant> cooldownUntil, List<ProviderId> keyRefusedBy) {
        return new ForecastSource(new ProviderId("open-meteo"), "Open-Meteo", CREDIT, FETCHED, freshness, role,
                primaryName, cooldownUntil, keyRefusedBy);
    }

    private static ForecastSource primary() {
        return source(Freshness.FRESH, ProviderRole.PRIMARY, "", Optional.empty(), List.of());
    }

    @Test
    void theFooterCreditsThePrimaryProviderAsItGivesItWithTheTimeItsForecastWasFetched() {
        assertThat(Footer.of(primary(), MADE_AT)).isEqualTo(new Footer(CREDIT, new Footer.Fallback.None(), FETCHED,
                false, Optional.empty(), List.of()));
    }

    @Test
    void aForecastOfTheFallbackSaysForWhichProviderItStoodIn() {
        var source = source(Freshness.FRESH, ProviderRole.FALLBACK, "Forecast.Solar", Optional.empty(), List.of());

        assertThat(Footer.of(source, MADE_AT).fallback()).isEqualTo(new Footer.Fallback.For("Forecast.Solar"));
    }

    @Test
    void aForecastOfTheFallbackForAPrimaryWithoutANameStoodInForAnUnknownOne() {
        var source = source(Freshness.FRESH, ProviderRole.FALLBACK, "", Optional.empty(), List.of());

        assertThat(Footer.of(source, MADE_AT).fallback()).isEqualTo(new Footer.Fallback.ForUnknown());
    }

    @Test
    void aPrimaryProvidersForecastStoodInForNoneEvenWithAPrimaryName() {
        var source = source(Freshness.FRESH, ProviderRole.PRIMARY, "Forecast.Solar", Optional.empty(), List.of());

        assertThat(Footer.of(source, MADE_AT).fallback()).isEqualTo(new Footer.Fallback.None());
    }

    @Test
    void aFallbackForAnEmptyNameIsRefused() {
        assertThatThrownBy(() -> new Footer.Fallback.For("")).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @EnumSource(Freshness.class)
    void onlyAForecastWhoseRefreshFailedIsStaleAndOneFromTheCacheIsNot(Freshness freshness) {
        var source = source(freshness, ProviderRole.PRIMARY, "", Optional.empty(), List.of());

        assertThat(Footer.of(source, MADE_AT).stale()).isEqualTo(freshness == Freshness.STALE);
    }

    @ParameterizedTest
    @CsvSource({"-60, false", "-1, false", "0, false", "1, true", "3600, true"})
    void theProviderIsBusyOnlyWhileItsCoolDownLastsPastTheMomentThePlanWasMade(long secondsAfter, boolean busy) {
        var until = MADE_AT.plusSeconds(secondsAfter);
        var source = source(Freshness.FRESH, ProviderRole.PRIMARY, "", Optional.of(until), List.of());

        assertThat(Footer.of(source, MADE_AT).busyUntil()).isEqualTo(busy ? Optional.of(until) : Optional.empty());
    }

    @Test
    void theProvidersThatRefusedTheirKeyAreInTheOrderTheyWereAsked() {
        var refused = List.of(new ProviderId("open-meteo"), new ProviderId("forecast-solar"));
        var source = source(Freshness.FRESH, ProviderRole.PRIMARY, "", Optional.empty(), refused);

        assertThat(Footer.of(source, MADE_AT).keyRefusedBy()).containsExactlyElementsOf(refused);
    }

    @Test
    void aFooterKeepsItsOwnListOfTheProvidersThatRefusedTheirKey() {
        var refused = new ArrayList<>(List.of(new ProviderId("open-meteo")));
        var footer = new Footer(CREDIT, new Footer.Fallback.None(), FETCHED, false, Optional.empty(), refused);

        refused.clear();

        assertThat(footer.keyRefusedBy()).hasSize(1);
    }
}
