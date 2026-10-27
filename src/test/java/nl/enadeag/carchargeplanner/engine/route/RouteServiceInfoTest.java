package nl.enadeag.carchargeplanner.engine.route;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import nl.enadeag.carchargeplanner.engine.config.ProviderId;
import nl.enadeag.carchargeplanner.engine.forecast.SettingSpec;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RouteServiceInfoTest {

    private static final List<SettingSpec> SETTINGS = List.of(
            new SettingSpec("api-key", "API key", "Your key.", false, true),
            new SettingSpec("region", "Region", "Where you are.", false, false),
            new SettingSpec("token", "Token", "Only for a paid plan.", true, true));

    private static RouteServiceInfo info(Duration cooldown) {
        return new RouteServiceInfo(new ProviderId("example"), "Example", "https://example.org/terms", "Example",
                "Everything.", cooldown, SETTINGS);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    void aCooldownMustBeMoreThanZero(long seconds) {
        assertThatThrownBy(() -> info(Duration.ofSeconds(seconds))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theRequiredSettingsThatAreMissingOrBlankAreNamedInOrder() {
        assertThat(info(Duration.ofSeconds(1)).missingRequired(Map.of("region", " ", "token", "t")))
                .containsExactly("API key", "Region");
        assertThat(info(Duration.ofSeconds(1)).missingRequired(Map.of("api-key", "k", "region", "r"))).isEmpty();
    }

    @Test
    void theSecretValuesAreThoseOfTheSecretSettingsThatHaveOne() {
        assertThat(info(Duration.ofSeconds(1)).secretValues(Map.of("api-key", "k", "region", "r", "token", " ")))
                .containsExactly("k");
    }
}
