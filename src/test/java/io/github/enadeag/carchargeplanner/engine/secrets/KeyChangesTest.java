package io.github.enadeag.carchargeplanner.engine.secrets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.enadeag.carchargeplanner.engine.config.ProviderId;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class KeyChangesTest {

    private static final ProviderId OPEN_METEO = new ProviderId("open-meteo");
    private static final ProviderId FORECAST_SOLAR = new ProviderId("forecast-solar");
    private static final Secrets STORED = new Secrets(Map.of(
            OPEN_METEO, Map.of("api_key", "OM-1"), FORECAST_SOLAR, Map.of("api_key", "FS-1")));

    @Test
    void whatTheOwnerChangedIsEnteredAndWhatTheyEmptiedIsRemoved() {
        var shown = new Secrets(Map.of(OPEN_METEO, Map.of("api_key", "OM-2")));

        var changes = KeyChanges.between(STORED, shown);

        assertThat(changes.entered()).isEqualTo(shown);
        assertThat(changes.removed()).isEqualTo(Map.of(FORECAST_SOLAR, Set.of("api_key")));
        assertThat(changes.applyTo(STORED)).isEqualTo(shown);
    }

    @Test
    void keysThatWereNotReadAreNeverRemovedAndStayWhenTheChangesAreApplied() {
        var entered = new Secrets(Map.of(OPEN_METEO, Map.of("api_key", "OM-2")));

        var changes = KeyChanges.between(Secrets.NONE, entered);

        assertThat(changes.removed()).isEmpty();
        assertThat(changes.applyTo(STORED)).isEqualTo(new Secrets(Map.of(
                OPEN_METEO, Map.of("api_key", "OM-2"), FORECAST_SOLAR, Map.of("api_key", "FS-1"))));
    }

    @Test
    void unchangedKeysAreNoChange() {
        assertThat(KeyChanges.between(STORED, STORED).isEmpty()).isTrue();
        assertThat(KeyChanges.NONE.applyTo(STORED)).isEqualTo(STORED);
    }

    @Test
    void aKeyCannotBeBothEnteredAndRemoved() {
        assertThatThrownBy(() -> new KeyChanges(new Secrets(Map.of(OPEN_METEO, Map.of("api_key", "x"))),
                Map.of(OPEN_METEO, Set.of("api_key")))).isInstanceOf(IllegalArgumentException.class);
    }
}
