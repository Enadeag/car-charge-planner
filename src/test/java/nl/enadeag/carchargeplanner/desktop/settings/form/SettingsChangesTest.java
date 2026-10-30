package nl.enadeag.carchargeplanner.desktop.settings.form;

import static org.assertj.core.api.Assertions.assertThat;

import nl.enadeag.carchargeplanner.desktop.app.AppSettings;
import nl.enadeag.carchargeplanner.desktop.app.Units;
import nl.enadeag.carchargeplanner.engine.config.EngineConfig;
import nl.enadeag.carchargeplanner.engine.config.PlanningPreferences;
import nl.enadeag.carchargeplanner.engine.config.ProviderId;
import nl.enadeag.carchargeplanner.engine.secrets.KeyChanges;
import nl.enadeag.carchargeplanner.engine.secrets.Secrets;
import java.time.Duration;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SettingsChangesTest {

    private static final ProviderId PROVIDER = new ProviderId("forecast-solar");
    private static final EngineConfig CONFIG = EngineConfig.setUpDraft(ZoneId.of("Europe/Amsterdam"), PROVIDER);
    private static final EngineConfig OTHER_CONFIG = CONFIG.withPlanning(new PlanningPreferences(
            PlanningPreferences.RECOMMENDED.minimumPct(), PlanningPreferences.RECOMMENDED.targetPct(), 0.5, 0.2,
            Duration.ofMinutes(45), Duration.ofMinutes(15)));
    private static final Secrets KEY = new Secrets(Map.of(PROVIDER, Map.of("api_key", "abc123")));
    private static final AppSettings METRIC = AppSettings.following(Units.METRIC);
    private static final AppSettings IMPERIAL = AppSettings.following(Units.IMPERIAL);
    private static final AllSettings STORED = new AllSettings(Optional.of(CONFIG), Secrets.NONE, METRIC,
            Optional.of(false));
    private static final SettingsChanges NONE = new SettingsChanges(Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty());

    @Test
    void whatIsEnteredAsStoredChangesNothing() {
        assertThat(SettingsChanges.between(STORED, STORED)).isEqualTo(NONE);
    }

    @Test
    void eachPartThatDiffersIsWritten() {
        var entered = new AllSettings(Optional.of(OTHER_CONFIG), KEY, IMPERIAL, Optional.of(true));

        assertThat(SettingsChanges.between(STORED, entered)).isEqualTo(new SettingsChanges(Optional.of(true),
                Optional.of(KeyChanges.between(Secrets.NONE, KEY)), Optional.of(OTHER_CONFIG), Optional.of(IMPERIAL)));
    }

    @Test
    void onlyThePartThatDiffersIsWritten() {
        assertThat(SettingsChanges.between(STORED, new AllSettings(Optional.of(CONFIG), Secrets.NONE, IMPERIAL,
                Optional.of(false)))).isEqualTo(new SettingsChanges(Optional.empty(), Optional.empty(),
                        Optional.empty(), Optional.of(IMPERIAL)));
        assertThat(SettingsChanges.between(STORED, new AllSettings(Optional.of(CONFIG), Secrets.NONE, METRIC,
                Optional.of(true)))).isEqualTo(new SettingsChanges(Optional.of(true), Optional.empty(),
                        Optional.empty(), Optional.empty()));
    }

    @Test
    void switchingStartAtLoginOffIsWrittenToo() {
        var stored = new AllSettings(Optional.of(CONFIG), Secrets.NONE, METRIC, Optional.of(true));

        assertThat(SettingsChanges.between(stored, new AllSettings(Optional.of(CONFIG), Secrets.NONE, METRIC,
                Optional.of(false))).startAtLogin()).contains(false);
    }

    @Test
    void whereThisCopyCannotStartAtLoginItIsNeverWritten() {
        var stored = new AllSettings(Optional.of(CONFIG), Secrets.NONE, METRIC, Optional.empty());

        assertThat(SettingsChanges.between(stored, new AllSettings(Optional.of(CONFIG), Secrets.NONE, METRIC,
                Optional.of(true)))).isEqualTo(NONE);
    }

    @Test
    void aConfigIsAlwaysWrittenWhenNoneWasStored() {
        var stored = new AllSettings(Optional.empty(), Secrets.NONE, METRIC, Optional.of(false));

        assertThat(SettingsChanges.between(stored, new AllSettings(Optional.of(CONFIG), Secrets.NONE, METRIC,
                Optional.of(false))).config()).contains(CONFIG);
    }

    @Test
    void withoutAConfigEnteredNeitherTheConfigNorTheKeysAreWritten() {
        var stored = new AllSettings(Optional.empty(), KEY, METRIC, Optional.of(false));

        assertThat(SettingsChanges.between(stored, new AllSettings(Optional.empty(), Secrets.NONE, IMPERIAL,
                Optional.of(false)))).isEqualTo(new SettingsChanges(Optional.empty(), Optional.empty(),
                        Optional.empty(), Optional.of(IMPERIAL)));
    }

    @Test
    void anEmptiedKeyIsWrittenAsRemoved() {
        var stored = new AllSettings(Optional.of(CONFIG), KEY, METRIC, Optional.of(false));

        assertThat(SettingsChanges.between(stored, new AllSettings(Optional.of(CONFIG), Secrets.NONE, METRIC,
                Optional.of(false))).keys()).contains(KeyChanges.between(KEY, Secrets.NONE));
    }
}
