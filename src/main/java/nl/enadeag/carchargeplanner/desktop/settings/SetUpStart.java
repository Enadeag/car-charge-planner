package nl.enadeag.carchargeplanner.desktop.settings;

import nl.enadeag.carchargeplanner.desktop.app.AppSettings;
import nl.enadeag.carchargeplanner.desktop.settings.form.AllSettings;
import nl.enadeag.carchargeplanner.engine.config.EngineConfig;
import nl.enadeag.carchargeplanner.engine.secrets.KeyStorage;
import nl.enadeag.carchargeplanner.engine.secrets.Secrets;
import nl.enadeag.carchargeplanner.engine.secrets.SecretsProblem;
import java.util.Optional;

/**
 * What is stored as set-up opens: Finish writes only what differs from it, and always a config when there was none.
 *
 * @param config the engine's config; empty when there is none that can be used, as before the first set-up
 * @param keys every provider's secret settings; none when they could not be read
 * @param keysProblem the kind of problem that kept the keys from being read; empty when they could be read
 * @param keyStorage where the keys are kept
 * @param app the app's own settings
 * @param startsAtLogin whether the system starts the app at login; empty when this copy of the app cannot
 */
record SetUpStart(Optional<EngineConfig> config, Secrets keys, Optional<SecretsProblem.Kind> keysProblem,
        KeyStorage keyStorage, AppSettings app, Optional<Boolean> startsAtLogin) {

    /**
     * Reads what is stored now. Blocks while the keychain, the files and the system answer: call it away from the
     * JavaFX thread.
     *
     * @param storage the stores to read
     * @return what they hold
     */
    static SetUpStart read(SettingsStorage storage) {
        var stored = storage.read();
        return new SetUpStart(stored.config(), stored.keys().keys(), stored.keys().problem(), stored.keyStorage(),
                stored.app(), stored.startsAtLogin());
    }

    /**
     * What is stored, to compare what is entered with.
     *
     * @return the config, the API keys, the app's own settings and start at login
     */
    AllSettings all() {
        return new AllSettings(config, keys, app, startsAtLogin);
    }

    /**
     * What set-up shows first: what is stored, or a first-run draft in place of a config there is not.
     *
     * @param firstRun the config to start from when there is none stored
     * @return the draft
     */
    SetUpDraft draft(EngineConfig firstRun) {
        return new SetUpDraft(config.orElse(firstRun), keys, app, startsAtLogin.orElse(false));
    }
}
