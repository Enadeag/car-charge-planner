package io.github.enadeag.carchargeplanner.desktop.settings.form;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.enadeag.carchargeplanner.desktop.text.Language;
import io.github.enadeag.carchargeplanner.desktop.text.Messages;
import io.github.enadeag.carchargeplanner.engine.config.ProviderId;
import io.github.enadeag.carchargeplanner.engine.forecast.ProviderInfo;
import io.github.enadeag.carchargeplanner.engine.forecast.SettingSpec;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProviderTextsTest {

    private static final SettingSpec API_KEY = new SettingSpec("api_key", "Key", "Engine's hint", true, true);
    private static final SettingSpec BASE_URL = new SettingSpec("base_url", "URL", "Engine's hint", true, false);
    private static final SettingSpec REGION = new SettingSpec("region", "Region", "Where you are.", true, false);

    private final ProviderTexts texts = new ProviderTexts(new Messages(Language.ENGLISH));

    private static ProviderInfo provider(String id) {
        return new ProviderInfo(new ProviderId(id), id, "The engine's description.", "", "", "the engine's words",
                Duration.ofHours(1), Duration.ofDays(2), Duration.ofHours(6), List.of(API_KEY, BASE_URL, REGION));
    }

    @Test
    void aProviderThisVersionKnowsIsDescribedInTheOwnersLanguage() {
        assertThat(texts.description(provider("open-meteo"))).startsWith("A free weather service run by Open-Meteo");
        assertThat(texts.sends(provider("open-meteo"))).startsWith("your location (latitude and longitude), your time");
        assertThat(texts.description(provider("forecast-solar"))).startsWith("A solar forecast service");
        assertThat(texts.sends(provider("forecast-solar"))).contains("their size in kWp");
    }

    @Test
    void anUnknownProviderKeepsTheEnginesEnglish() {
        assertThat(texts.description(provider("elsewhere"))).isEqualTo("The engine's description.");
        assertThat(texts.sends(provider("elsewhere"))).isEqualTo("the engine's words");
        assertThat(texts.label(REGION)).isEqualTo("Region");
        assertThat(texts.hint(provider("elsewhere"), API_KEY)).isEqualTo("Engine's hint");
        assertThat(texts.hint(provider("open-meteo"), REGION)).isEqualTo("Where you are.");
    }

    @Test
    void knownSettingsHaveTheirOwnNameAndHint() {
        assertThat(texts.label(API_KEY)).isEqualTo("API key");
        assertThat(texts.label(BASE_URL)).isEqualTo("Server address");
        assertThat(texts.hint(provider("open-meteo"), API_KEY)).startsWith("Only for a paid plan (commercial use)");
        assertThat(texts.hint(provider("open-meteo"), BASE_URL)).startsWith("Leave empty for the free public service");
        assertThat(texts.hint(provider("forecast-solar"), API_KEY)).contains("more days and finer steps");
        assertThat(texts.hint(provider("forecast-solar"), BASE_URL)).isEqualTo("Engine's hint");
    }
}
