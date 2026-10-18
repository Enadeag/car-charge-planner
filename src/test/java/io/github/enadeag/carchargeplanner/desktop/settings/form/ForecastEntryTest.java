package io.github.enadeag.carchargeplanner.desktop.settings.form;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.enadeag.carchargeplanner.engine.config.ForecastSelection;
import io.github.enadeag.carchargeplanner.engine.config.ProviderId;
import io.github.enadeag.carchargeplanner.engine.setting.InvalidSetting;
import io.github.enadeag.carchargeplanner.engine.setting.Setting;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ForecastEntryTest {

    private static final ProviderId OPEN_METEO = new ProviderId("open-meteo");
    private static final ProviderId FORECAST_SOLAR = new ProviderId("forecast-solar");
    private static final ProviderId GONE = new ProviderId("gone");

    @Test
    void theChosenProvidersAreThePrimaryThenTheFallback() {
        assertThat(new ForecastEntry(OPEN_METEO, Optional.of(FORECAST_SOLAR)).chosen())
                .containsExactly(OPEN_METEO, FORECAST_SOLAR);
        assertThat(new ForecastEntry(OPEN_METEO, Optional.empty()).chosen()).containsExactly(OPEN_METEO);
        assertThat(new ForecastEntry(OPEN_METEO, Optional.of(OPEN_METEO)).chosen()).containsExactly(OPEN_METEO);
    }

    @Test
    void theShownSettingsGoOverTheStoredOnesAndProvidersWithoutAnyAreLeftOut() {
        var stored = Map.of(GONE, Map.of("region", "north"), OPEN_METEO, Map.of("base_url", "https://old.example"));
        var shown = Map.<ProviderId, Map<String, String>>of(OPEN_METEO, Map.of(), FORECAST_SOLAR,
                Map.of("base_url", "https://new.example"));

        assertThat(new ForecastEntry(FORECAST_SOLAR, Optional.empty()).selection(stored, shown))
                .isEqualTo(new ForecastSelection(FORECAST_SOLAR, Optional.empty(), Map.of(GONE,
                        Map.of("region", "north"), FORECAST_SOLAR, Map.of("base_url", "https://new.example"))));
    }

    @Test
    void theFallbackCannotBeThePrimary() {
        assertThatThrownBy(() -> new ForecastEntry(OPEN_METEO, Optional.of(OPEN_METEO)).selection(Map.of(), Map.of()))
                .isInstanceOfSatisfying(InvalidSetting.class,
                        refused -> assertThat(refused.setting()).isEqualTo(Setting.FALLBACK_PROVIDER));
    }
}
