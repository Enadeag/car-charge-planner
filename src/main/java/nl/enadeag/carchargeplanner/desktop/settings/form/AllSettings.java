package nl.enadeag.carchargeplanner.desktop.settings.form;

import nl.enadeag.carchargeplanner.desktop.app.AppSettings;
import nl.enadeag.carchargeplanner.engine.calendar.Consent;
import nl.enadeag.carchargeplanner.engine.config.EngineConfig;
import nl.enadeag.carchargeplanner.engine.secrets.Secrets;
import java.util.Optional;
import java.util.Set;

/**
 * Everything Settings and set-up save, as stored or as entered.
 *
 * @param config the engine's whole config; empty when there is none that can be used
 * @param keys every provider's secret settings
 * @param app the app's own settings
 * @param startsAtLogin whether the app starts at login; empty when this copy of the app cannot
 * @param consents the calendar's consents that are given
 */
public record AllSettings(Optional<EngineConfig> config, Secrets keys, AppSettings app,
        Optional<Boolean> startsAtLogin, Set<Consent> consents) {
}
