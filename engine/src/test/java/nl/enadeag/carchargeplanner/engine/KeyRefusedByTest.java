package nl.enadeag.carchargeplanner.engine;

import static org.assertj.core.api.Assertions.assertThat;

import nl.enadeag.carchargeplanner.engine.config.ProviderId;
import nl.enadeag.carchargeplanner.engine.forecast.ForecastSource;
import nl.enadeag.carchargeplanner.engine.forecast.ForecastUnavailable;
import nl.enadeag.carchargeplanner.engine.forecast.Freshness;
import nl.enadeag.carchargeplanner.engine.forecast.ProviderRole;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The providers that refused their key, as each value that names them keeps them: a copy, not the caller's list. */
class KeyRefusedByTest {

    private static final ProviderId MAIN = new ProviderId("main");
    private static final ProviderId BACKUP = new ProviderId("backup");

    private List<ProviderId> refused;

    @BeforeEach
    void theMainProviderRefused() {
        refused = new ArrayList<>(List.of(MAIN));
    }

    @Test
    void aForecastSourceKeepsTheProvidersAsTheyWereWhenItWasMade() {
        var source = new ForecastSource(MAIN, "Main", "Forecast by Main", Instant.EPOCH, Freshness.STALE,
                ProviderRole.PRIMARY, "", Optional.empty(), refused);

        refused.add(BACKUP);

        assertThat(source.keyRefusedBy()).containsExactly(MAIN);
    }

    @Test
    void aForecastThatIsUnavailableKeepsTheProvidersAsTheyWereWhenItWasMade() {
        var unavailable = new ForecastUnavailable("Main: refused", false, refused);

        refused.add(BACKUP);

        assertThat(unavailable.keyRefusedBy()).containsExactly(MAIN);
    }

    @Test
    void anOutlookWithoutAForecastKeepsTheProvidersAsTheyWereWhenItWasMade() {
        var none = new Outlook.NoForecast("Main: refused", false, refused);

        refused.add(BACKUP);

        assertThat(none.keyRefusedBy()).containsExactly(MAIN);
    }
}
