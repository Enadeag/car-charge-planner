package nl.enadeag.carchargeplanner.desktop.settings.form;

import nl.enadeag.carchargeplanner.desktop.app.AppSettings;
import nl.enadeag.carchargeplanner.engine.config.EngineConfig;
import nl.enadeag.carchargeplanner.engine.secrets.KeyChanges;
import java.util.Optional;

/**
 * What to write; each part only when it is present.
 *
 * @param startAtLogin whether the app should start at login; present when the owner changed it
 * @param keys what the owner entered and emptied in the API keys, applied to the stored ones; present when they
 *     changed
 * @param config the engine's whole config; present when it changed, or was never saved
 * @param app the app's own settings; present when they changed
 */
public record SettingsChanges(Optional<Boolean> startAtLogin, Optional<KeyChanges> keys,
        Optional<EngineConfig> config, Optional<AppSettings> app) {

    /**
     * What differs between what is stored and what is entered. Start at login counts only where this copy of the app
     * can start at login, and the API keys only with a config entered: without one, no provider's fields were shown.
     *
     * @param stored what the stores hold
     * @param entered what the owner entered
     * @return the parts of {@code entered} to write
     */
    public static SettingsChanges between(AllSettings stored, AllSettings entered) {
        var startAtLogin = stored.startsAtLogin()
                .flatMap(was -> entered.startsAtLogin().filter(now -> !now.equals(was)));
        var keys = entered.config().map(_ -> KeyChanges.between(stored.keys(), entered.keys()))
                .filter(changed -> !changed.isEmpty());
        var config = entered.config().filter(changed -> !Optional.of(changed).equals(stored.config()));
        var app = Optional.of(entered.app()).filter(changed -> !changed.equals(stored.app()));
        return new SettingsChanges(startAtLogin, keys, config, app);
    }
}
