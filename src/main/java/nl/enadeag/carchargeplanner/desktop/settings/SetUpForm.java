package nl.enadeag.carchargeplanner.desktop.settings;

import nl.enadeag.carchargeplanner.desktop.app.AppSettings;
import nl.enadeag.carchargeplanner.desktop.settings.form.AllSettings;
import nl.enadeag.carchargeplanner.desktop.settings.form.SettingsChanges;
import nl.enadeag.carchargeplanner.engine.config.EngineConfig;
import nl.enadeag.carchargeplanner.engine.secrets.Secrets;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * What set-up's steps show against what is stored: it loads the steps' parts from a draft, has the engine's records
 * check a step's values, and at Finish writes what differs from what is stored, through the same storage as Settings.
 *
 * <p>{@link #write} blocks and runs away from the JavaFX thread; the rest belongs to the JavaFX thread.
 */
final class SetUpForm {

    private final SettingsStorage storage;
    private final Map<SetUpStep, List<ConfigPart>> parts;
    private final LocationPage location;
    private final ForecastPage forecast;
    private final AppPage app;
    private SetUpStart stored;
    private EngineConfig base;

    /**
     * Makes the form; nothing is shown until {@link #start(SetUpStart, SetUpDraft)}.
     *
     * @param storage writes at Finish
     * @param parts per step the parts of the config it shows; a step without any is left out
     * @param location the location page, one of the parts, which also needs a place chosen
     * @param forecast the forecast page, one of the parts, which also holds the API keys
     * @param app the page of the app's own settings
     */
    SetUpForm(SettingsStorage storage, Map<SetUpStep, List<ConfigPart>> parts, LocationPage location,
            ForecastPage forecast, AppPage app) {
        this.storage = storage;
        this.parts = new EnumMap<>(parts);
        this.location = location;
        this.forecast = forecast;
        this.app = app;
    }

    /**
     * Shows a draft on every step, as the values not edited.
     *
     * @param start what is stored, which Finish compares with
     * @param draft what to show
     */
    void start(SetUpStart start, SetUpDraft draft) {
        stored = start;
        base = draft.config();
        forecast.loadKeys(draft.keys(), start.keyStorage(), start.keysProblem());
        allParts().forEach(part -> part.load(base));
        app.load(draft.app(), start.startsAtLogin().map(possible -> draft.startAtLogin()));
    }

    /**
     * Has the engine's records check a step's values; a refused one shows its problem at its field.
     *
     * @param step the step to check; one without config values always passes
     * @return true when every value is accepted, and on the location step a place is chosen
     */
    boolean check(SetUpStep step) {
        var accepted = true;
        for (var part : parts.getOrDefault(step, List.of())) {
            accepted &= part.applyTo(base).isPresent();
        }
        if (step == SetUpStep.LOCATION && accepted) {
            accepted = location.checkChosen();
        }
        return accepted;
    }

    /**
     * The first step with a value that is refused, such as one changed after going back.
     *
     * @return that step, showing its problems; empty when every step's values are accepted
     */
    Optional<SetUpStep> firstRefused() {
        for (var step : SetUpStep.values()) {
            if (!check(step)) {
                return Optional.of(step);
            }
        }
        return Optional.empty();
    }

    /**
     * The whole config as entered; call when {@link #firstRefused()} found nothing.
     *
     * @return the config with every step's values; the draft's for any value that is refused
     */
    EngineConfig config() {
        var config = base;
        for (var part : allParts()) {
            config = part.applyTo(config).orElse(config);
        }
        return config;
    }

    /**
     * The API keys as entered.
     *
     * @return every provider's secret settings
     */
    Secrets keys() {
        return forecast.keys();
    }

    /**
     * Everything as entered, also what is refused now, to carry over to set-up made again in another language.
     *
     * @return the draft; a refused value is the one it had before
     */
    SetUpDraft draft() {
        return new SetUpDraft(config(), keys(), app.settings(), app.startsAtLogin());
    }

    /**
     * Whether the owner changed something since set-up was started.
     *
     * @return true when leaving would throw edits away
     */
    boolean isChanged() {
        return app.isChanged() || allParts().stream().anyMatch(ConfigPart::isChanged);
    }

    /**
     * What differs from what is stored: the config always when none was stored.
     *
     * @param config the whole config to keep, as {@link #config()} gave it
     * @return the changes for {@link #write}
     */
    SettingsChanges changes(EngineConfig config) {
        var entered = new AllSettings(Optional.of(config), keys(), app.settings(), Optional.of(app.startsAtLogin()));
        return SettingsChanges.between(stored.all(), entered);
    }

    /**
     * Writes the changes. Blocks while the system, the keychain and the files answer; may be called from any thread.
     *
     * @param changes what {@link #changes(EngineConfig)} gave
     * @param prompting runs, on the saving thread, once the system asks the owner to unlock the keychain
     * @return saved; refused by the system's start at login; not saved as the keychain stayed locked; or failed with
     *     the system's error
     */
    SaveOutcome write(SettingsChanges changes, Runnable prompting) {
        return storage.save(changes, prompting);
    }

    /**
     * The app's own settings as entered, which Finish stores.
     *
     * @return the settings on the App step
     */
    AppSettings appSettings() {
        return app.settings();
    }

    /**
     * Stops a write that waits for the owner to unlock the system keychain, which then comes to
     * {@link SaveOutcome.KeyringLocked}; nothing happens when none waits. Never blocks for long.
     */
    void stopUnlocking() {
        storage.stopUnlocking();
    }

    /**
     * Shows at start at login why the system refused it.
     *
     * @param detail the system's reason
     */
    void showStartAtLoginFailure(String detail) {
        app.showStartAtLoginFailure(detail);
    }

    private List<ConfigPart> allParts() {
        return parts.values().stream().flatMap(List::stream).toList();
    }
}
