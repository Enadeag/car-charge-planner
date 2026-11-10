package nl.enadeag.carchargeplanner.desktop.settings.setup;

import nl.enadeag.carchargeplanner.desktop.app.AppSettings;
import nl.enadeag.carchargeplanner.desktop.settings.store.SettingsStorage;
import nl.enadeag.carchargeplanner.engine.config.EngineConfig;
import nl.enadeag.carchargeplanner.engine.secrets.Secrets;

record SetUpDraft(EngineConfig config, Secrets keys, AppSettings app, boolean startAtLogin) {

    /**
     * What set-up shows first: what is stored, or a first-run draft in place of a config there is not.
     *
     * @return the draft; start at login off when this copy of the app cannot start at login
     */
    static SetUpDraft of(SettingsStorage.Stored stored, EngineConfig firstRun) {
        return new SetUpDraft(stored.config().orElse(firstRun), stored.keys().keys(), stored.app(),
                stored.startsAtLogin().orElse(false));
    }

    /**
     * The draft with the language and appearance of other app settings, such as ones saved in Settings meanwhile.
     */
    SetUpDraft withLanguageAndAppearanceOf(AppSettings saved) {
        return new SetUpDraft(config, keys,
                new AppSettings(saved.language(), app.units(), app.mode(), saved.appearance()), startAtLogin);
    }
}
