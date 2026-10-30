package nl.enadeag.carchargeplanner.desktop.settings.setup;

import nl.enadeag.carchargeplanner.desktop.app.AppSettings;
import nl.enadeag.carchargeplanner.desktop.settings.store.SettingsStorage;
import nl.enadeag.carchargeplanner.engine.config.EngineConfig;
import nl.enadeag.carchargeplanner.engine.secrets.Secrets;

/**
 * What set-up holds, none of it saved yet.
 *
 * @param config the engine's whole config as entered
 * @param keys every provider's secret settings as entered
 * @param app the app's own settings as chosen
 * @param startAtLogin whether the app should start at login
 */
record SetUpDraft(EngineConfig config, Secrets keys, AppSettings app, boolean startAtLogin) {

    /**
     * What set-up shows first: what is stored, or a first-run draft in place of a config there is not.
     *
     * @param stored what is stored as set-up opens
     * @param firstRun the config to start from when there is none stored that can be used
     * @return the draft; start at login off when this copy of the app cannot start at login
     */
    static SetUpDraft of(SettingsStorage.Stored stored, EngineConfig firstRun) {
        return new SetUpDraft(stored.config().orElse(firstRun), stored.keys().keys(), stored.app(),
                stored.startsAtLogin().orElse(false));
    }

    /**
     * The draft with the language and appearance of other app settings, such as ones saved in Settings meanwhile.
     *
     * @param saved the app settings whose language and appearance to take
     * @return the same draft otherwise
     */
    SetUpDraft withLanguageAndAppearanceOf(AppSettings saved) {
        return new SetUpDraft(config, keys,
                new AppSettings(saved.language(), app.units(), app.mode(), saved.appearance()), startAtLogin);
    }
}
