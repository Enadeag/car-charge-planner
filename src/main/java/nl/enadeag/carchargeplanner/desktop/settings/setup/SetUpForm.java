package nl.enadeag.carchargeplanner.desktop.settings.setup;

import nl.enadeag.carchargeplanner.desktop.app.AppSettings;
import nl.enadeag.carchargeplanner.desktop.settings.app.AppPage;
import nl.enadeag.carchargeplanner.desktop.settings.forecast.ForecastPage;
import nl.enadeag.carchargeplanner.desktop.settings.form.AllSettings;
import nl.enadeag.carchargeplanner.desktop.settings.form.SettingsChanges;
import nl.enadeag.carchargeplanner.desktop.settings.location.LocationPage;
import nl.enadeag.carchargeplanner.desktop.settings.page.ConfigPart;
import nl.enadeag.carchargeplanner.desktop.settings.store.SaveOutcome;
import nl.enadeag.carchargeplanner.desktop.settings.store.SettingsStorage;
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
    private SettingsStorage.Stored stored;
    private EngineConfig base;

    /**
     * Makes the form; nothing is shown until {@link #start(SettingsStorage.Stored, SetUpDraft)}.
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
    void start(SettingsStorage.Stored start, SetUpDraft draft) {
        stored = start;
        base = draft.config();
        start.keys().problem().ifPresentOrElse(
                problem -> forecast.loadKeys(draft.keys(), start.keyStorage(), problem),
                () -> forecast.loadKeys(draft.keys(), start.keyStorage()));
        allParts().forEach(part -> part.load(base));
        start.startsAtLogin().ifPresentOrElse(_ -> app.load(draft.app(), draft.startAtLogin()),
                () -> app.loadWithoutStartAtLogin(draft.app()));
    }

    /**
     * Has the engine's records check a step's values, as Next does, and marks each refused one by showing its problem
     * at its field; the other steps' fields do not change.
     *
     * @param step the step to check; one without config values is never refused
     * @return true when a value is refused, or on the location step no place is chosen
     */
    boolean markRefused(SetUpStep step) {
        var refused = false;
        for (var part : parts.getOrDefault(step, List.of())) {
            refused |= part.showProblems();
        }
        if (step == SetUpStep.LOCATION && !refused) {
            refused = location.markNotChosen();
        }
        return refused;
    }

    /**
     * Finds the first step with a value that is refused, such as one changed after going back, and marks its refused
     * values; the steps before it show no problems.
     *
     * @return that step; empty when every step's values are accepted
     */
    Optional<SetUpStep> markFirstRefused() {
        for (var step : SetUpStep.values()) {
            if (markRefused(step)) {
                return Optional.of(step);
            }
        }
        return Optional.empty();
    }

    /**
     * The whole config as entered: the draft, with each part's values laid over it in the order of the steps. A part
     * with a refused value adds none of its values, so the draft's stay; call {@link #markFirstRefused()} first for a
     * config to save. Only reads, so a step can ask it as the owner reaches it: no field shows or loses a problem.
     *
     * @return the config with every step's accepted values
     */
    EngineConfig config() {
        var config = base;
        for (var part : allParts()) {
            config = part.read(config).orElse(config);
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
     * What differs from what is stored: the whole config as entered, {@link #config()}, always when none was stored.
     * Call when {@link #markFirstRefused()} found nothing.
     *
     * @return the changes for {@link #write}
     */
    SettingsChanges changes() {
        var all = stored.all();
        var entered = new AllSettings(Optional.of(config()), keys(), app.settings(), Optional.of(app.startsAtLogin()),
                all.consents());
        return SettingsChanges.between(all, entered);
    }

    /**
     * Writes the changes. Blocks while the system, the keychain and the files answer; may be called from any thread.
     *
     * @param changes what {@link #changes()} gave
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
     * {@link SaveOutcome.KeychainLocked}; nothing happens when none waits. Never blocks for long.
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
