package nl.enadeag.carchargeplanner.engine.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ProviderIdTest {

    @ParameterizedTest
    @ValueSource(strings = {"open-meteo", "forecast-solar", "a", "7", "solcast2", "a-"})
    void lowerCaseLettersDigitsAndHyphensAreAnId(String text) {
        assertThat(new ProviderId(text).value()).isEqualTo(text);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "-open", "Open-Meteo", "open meteo", "open/meteo", "open_meteo", "open-météo",
        "open-meteo\n"})
    void anythingElseIsRefused(String text) {
        assertThatThrownBy(() -> new ProviderId(text))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("is not a provider id");
    }

    @Test
    void idsWithTheSameTextAreEqual() {
        assertThat(new ProviderId("open-meteo"))
                .isEqualTo(new ProviderId("open-meteo"))
                .hasSameHashCodeAs(new ProviderId("open-meteo"))
                .isNotEqualTo(new ProviderId("forecast-solar"));
    }

    @Test
    void itShowsAsItsText() {
        assertThat("provider '" + new ProviderId("open-meteo") + "'").isEqualTo("provider 'open-meteo'");
    }
}
