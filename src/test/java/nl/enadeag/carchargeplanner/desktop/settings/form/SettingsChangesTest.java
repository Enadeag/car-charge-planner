package nl.enadeag.carchargeplanner.desktop.settings.form;

import static org.assertj.core.api.Assertions.assertThat;

import nl.enadeag.carchargeplanner.desktop.app.AppSettings;
import nl.enadeag.carchargeplanner.desktop.app.Units;
import nl.enadeag.carchargeplanner.engine.calendar.CalendarSecrets;
import nl.enadeag.carchargeplanner.engine.calendar.Consent;
import nl.enadeag.carchargeplanner.engine.config.CalendarSettings;
import nl.enadeag.carchargeplanner.engine.config.EngineConfig;
import nl.enadeag.carchargeplanner.engine.config.PlanningPreferences;
import nl.enadeag.carchargeplanner.engine.config.ProviderId;
import nl.enadeag.carchargeplanner.engine.secrets.KeyChanges;
import nl.enadeag.carchargeplanner.engine.secrets.Secrets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
            Optional.of(false), Set.of());
    private static final SettingsChanges NONE = new SettingsChanges(Optional.empty(), Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty());

    @Test
    void whatIsEnteredAsStoredChangesNothing() {
        assertThat(SettingsChanges.between(STORED, STORED)).isEqualTo(NONE);
    }

    @Test
    void eachPartThatDiffersIsWritten() {
        var entered = new AllSettings(Optional.of(OTHER_CONFIG), KEY, IMPERIAL, Optional.of(true), Set.of());

        assertThat(SettingsChanges.between(STORED, entered)).isEqualTo(new SettingsChanges(Optional.of(true), Optional.empty(),
                Optional.of(KeyChanges.between(Secrets.NONE, KEY)), Optional.of(OTHER_CONFIG), Optional.of(IMPERIAL)));
    }

    @Test
    void onlyThePartThatDiffersIsWritten() {
        assertThat(SettingsChanges.between(STORED, new AllSettings(Optional.of(CONFIG), Secrets.NONE, IMPERIAL,
                Optional.of(false), Set.of()))).isEqualTo(new SettingsChanges(Optional.empty(), Optional.empty(), Optional.empty(),
                        Optional.empty(), Optional.of(IMPERIAL)));
        assertThat(SettingsChanges.between(STORED, new AllSettings(Optional.of(CONFIG), Secrets.NONE, METRIC,
                Optional.of(true), Set.of()))).isEqualTo(new SettingsChanges(Optional.of(true), Optional.empty(), Optional.empty(),
                        Optional.empty(), Optional.empty()));
    }

    @Test
    void switchingStartAtLoginOffIsWrittenToo() {
        var stored = new AllSettings(Optional.of(CONFIG), Secrets.NONE, METRIC, Optional.of(true), Set.of());

        assertThat(SettingsChanges.between(stored, new AllSettings(Optional.of(CONFIG), Secrets.NONE, METRIC,
                Optional.of(false), Set.of())).startAtLogin()).contains(false);
    }

    @Test
    void whereThisCopyCannotStartAtLoginItIsNeverWritten() {
        var stored = new AllSettings(Optional.of(CONFIG), Secrets.NONE, METRIC, Optional.empty(), Set.of());

        assertThat(SettingsChanges.between(stored, new AllSettings(Optional.of(CONFIG), Secrets.NONE, METRIC,
                Optional.of(true), Set.of()))).isEqualTo(NONE);
    }

    @Test
    void aConfigIsAlwaysWrittenWhenNoneWasStored() {
        var stored = new AllSettings(Optional.empty(), Secrets.NONE, METRIC, Optional.of(false), Set.of());

        assertThat(SettingsChanges.between(stored, new AllSettings(Optional.of(CONFIG), Secrets.NONE, METRIC,
                Optional.of(false), Set.of())).config()).contains(CONFIG);
    }

    @Test
    void withoutAConfigEnteredNeitherTheConfigNorTheKeysAreWritten() {
        var stored = new AllSettings(Optional.empty(), KEY, METRIC, Optional.of(false), Set.of());

        assertThat(SettingsChanges.between(stored, new AllSettings(Optional.empty(), Secrets.NONE, IMPERIAL,
                Optional.of(false), Set.of()))).isEqualTo(new SettingsChanges(Optional.empty(), Optional.empty(), Optional.empty(),
                        Optional.empty(), Optional.of(IMPERIAL)));
    }

    @Test
    void anEmptiedKeyIsWrittenAsRemoved() {
        var stored = new AllSettings(Optional.of(CONFIG), KEY, METRIC, Optional.of(false), Set.of());

        assertThat(SettingsChanges.between(stored, new AllSettings(Optional.of(CONFIG), Secrets.NONE, METRIC,
                Optional.of(false), Set.of())).keys()).contains(KeyChanges.between(KEY, Secrets.NONE));
    }

    private static final Set<Consent> BOTH = Set.of(Consent.READ_CALENDAR, Consent.SEND_LOCATIONS);
    private static final Secrets LINK = new Secrets(Map.of(CalendarSecrets.CALENDAR,
            Map.of(CalendarSecrets.ICAL_LINK, "https://calendar.example/secret.ics")));
    private static final EngineConfig WITH_FILE = CONFIG.withCalendar(new CalendarSettings(
            Optional.of(Path.of("calendar.ics").toAbsolutePath()), Duration.ofMinutes(20), LocalTime.of(8, 0),
            LocalTime.of(19, 0)));

    @Test
    void givenConsentsAreWritten() {
        var stored = new AllSettings(Optional.of(CONFIG), Secrets.NONE, METRIC, Optional.of(false), Set.of());

        assertThat(SettingsChanges.between(stored, new AllSettings(Optional.of(CONFIG), Secrets.NONE, METRIC,
                Optional.of(false), BOTH)).consents()).contains(BOTH);
    }

    @Test
    void withoutAConfigEnteredNoConsentIsWritten() {
        var stored = new AllSettings(Optional.empty(), Secrets.NONE, METRIC, Optional.of(false), Set.of());

        assertThat(SettingsChanges.between(stored, new AllSettings(Optional.empty(), Secrets.NONE, METRIC,
                Optional.of(false), BOTH)).consents()).isEmpty();
    }

    @Test
    void aWithdrawnConsentWipesTheLinkAndTheFileWhateverThePageShows() {
        var stored = new AllSettings(Optional.of(WITH_FILE), LINK, METRIC, Optional.of(false), BOTH);

        var changes = SettingsChanges.between(stored, new AllSettings(Optional.of(WITH_FILE), LINK, METRIC,
                Optional.of(false), Set.of(Consent.READ_CALENDAR)));

        assertThat(changes.consents()).contains(Set.of(Consent.READ_CALENDAR));
        assertThat(changes.keys().orElseThrow().applyTo(LINK)).isEqualTo(Secrets.NONE);
        assertThat(changes.config().orElseThrow().calendar()).isEqualTo(new CalendarSettings(Optional.empty(),
                Duration.ofMinutes(20), LocalTime.of(8, 0), LocalTime.of(19, 0)));
    }

    @Test
    void aWithdrawnConsentRemovesALinkThatCouldNotBeReadAndKeepsTheOtherKeys() {
        var stored = new AllSettings(Optional.of(CONFIG), KEY, METRIC, Optional.of(false), BOTH);

        var changes = SettingsChanges.between(stored, new AllSettings(Optional.of(CONFIG), KEY, METRIC,
                Optional.of(false), Set.of()));

        var keychain = new Secrets(Map.of(PROVIDER, Map.of("api_key", "abc123"), CalendarSecrets.CALENDAR,
                Map.of(CalendarSecrets.ICAL_LINK, "https://calendar.example/secret.ics")));
        assertThat(changes.keys().orElseThrow().applyTo(keychain)).isEqualTo(KEY);
    }

    @Test
    void consentGivenAsWellWipesNothing() {
        var stored = new AllSettings(Optional.of(WITH_FILE), LINK, METRIC, Optional.of(false),
                Set.of(Consent.READ_CALENDAR));

        var changes = SettingsChanges.between(stored, new AllSettings(Optional.of(WITH_FILE), LINK, METRIC,
                Optional.of(false), BOTH));

        assertThat(changes).isEqualTo(new SettingsChanges(Optional.empty(), Optional.of(BOTH), Optional.empty(),
                Optional.empty(), Optional.empty()));
    }
}
