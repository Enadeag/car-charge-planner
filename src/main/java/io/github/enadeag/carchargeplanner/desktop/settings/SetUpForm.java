package io.github.enadeag.carchargeplanner.desktop.settings;

import io.github.enadeag.carchargeplanner.engine.config.EngineConfig;
import io.github.enadeag.carchargeplanner.engine.secrets.Secrets;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * What set-up's steps show against what is stored: it loads the steps' parts from a draft, has the engine's records
 * check a step's values, and at Finish writes what differs from what is stored, through the same storage as Settings.
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
     * @param storage reads the key storage and writes at Finish
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
        forecast.loadKeys(draft.keys(), storage.keyStorage(), start.keysProblem());
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
     * Writes what differs from what is stored: the config always when none was stored.
     *
     * @param config the whole config to keep, as {@link #config()} gave it
     * @return saved; refused with the App category when the system refused start at login; or failed with the
     *     system's error
     */
    SaveOutcome save(EngineConfig config) {
        var startAtLogin = stored.startsAtLogin().filter(on -> on != app.startsAtLogin()).map(on -> app.startsAtLogin());
        var keys = Optional.of(keys()).filter(entered -> !entered.equals(stored.keys()));
        var newConfig = Optional.of(config).filter(entered -> !Optional.of(entered).equals(stored.config()));
        var settings = Optional.of(app.settings()).filter(chosen -> !chosen.equals(stored.app()));
        return storage.save(new SettingsStorage.Changes(startAtLogin, keys, newConfig, settings), app);
    }

    private List<ConfigPart> allParts() {
        return parts.values().stream().flatMap(List::stream).toList();
    }
}
