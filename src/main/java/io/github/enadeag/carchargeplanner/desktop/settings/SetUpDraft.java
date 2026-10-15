package io.github.enadeag.carchargeplanner.desktop.settings;

import io.github.enadeag.carchargeplanner.desktop.AppSettings;
import io.github.enadeag.carchargeplanner.engine.config.EngineConfig;
import io.github.enadeag.carchargeplanner.engine.secrets.Secrets;

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
     * The draft with the language and appearance of other app settings, such as ones saved in Settings meanwhile.
     *
     * @param saved the app settings whose language and appearance to take
     * @return the same draft otherwise
     */
    SetUpDraft withLooksOf(AppSettings saved) {
        return new SetUpDraft(config, keys,
                new AppSettings(saved.language(), app.units(), app.mode(), saved.appearance()), startAtLogin);
    }
}
