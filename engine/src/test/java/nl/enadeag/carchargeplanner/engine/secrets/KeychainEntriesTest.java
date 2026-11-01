package nl.enadeag.carchargeplanner.engine.secrets;

import static org.assertj.core.api.Assertions.assertThat;

import nl.enadeag.carchargeplanner.engine.config.ProviderId;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class KeychainEntriesTest {

    @Test
    void whatWasReadIsDescribedByNameAndNeverByValue() {
        var contents = new KeychainEntries.Contents(
                new Secrets(Map.of(new ProviderId("open-meteo"), Map.of("api_key", "OM-SECRET-1"))),
                Map.of(new ProviderId("forecast-solar"), Set.of("api_key")),
                Set.of("forecast/open-meteo/api_key", "forecast/forecast-solar/api_key"));

        assertThat(contents.toString()).doesNotContain("OM-SECRET-1")
                .isEqualTo("Contents[readable=[forecast/open-meteo/api_key], "
                        + "unreadable=[forecast/forecast-solar/api_key], "
                        + "listed=[forecast/forecast-solar/api_key, forecast/open-meteo/api_key]]");
    }
}
