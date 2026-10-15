package io.github.enadeag.carchargeplanner.engine.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

class ProviderIdTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

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

    @Test
    void inJsonAnIdIsItsText() {
        var id = new ProviderId("open-meteo");

        assertThat(JSON.writeValueAsString(id)).isEqualTo("\"open-meteo\"");
        assertThat(JSON.readValue("\"open-meteo\"", ProviderId.class)).isEqualTo(id);
    }

    @Test
    void asAnObjectsKeyAnIdIsItsText() {
        var byProvider = Map.of(new ProviderId("open-meteo"), "x");
        var json = "{\"open-meteo\":\"x\"}";

        assertThat(JSON.writeValueAsString(byProvider)).isEqualTo(json);
        assertThat(JSON.readValue(json, new TypeReference<Map<ProviderId, String>>() { })).isEqualTo(byProvider);
    }

    @Test
    void inJsonAWrongIdIsRefused() {
        assertThatThrownBy(() -> JSON.readValue("\"Open Meteo\"", ProviderId.class))
                .hasRootCauseMessage("'Open Meteo' is not a provider id: lower case letters, digits and hyphens, "
                        + "such as open-meteo");
        assertThatThrownBy(() -> JSON.readValue("{\"Open Meteo\":\"x\"}", new TypeReference<Map<ProviderId, String>>() { }))
                .hasMessageContaining("is not a provider id");
    }
}
