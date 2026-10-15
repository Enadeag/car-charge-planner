package io.github.enadeag.carchargeplanner.desktop.settings;

import io.github.enadeag.carchargeplanner.desktop.AppSettings;
import io.github.enadeag.carchargeplanner.engine.config.ConfigProblem;
import io.github.enadeag.carchargeplanner.engine.config.EngineConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * What Settings shows against what is stored: it loads the pages from the stores, tells whether something changed,
 * and on Save has the engine check and keep its part, the system its start at login, and the app settings file the
 * rest.
 */
final class SettingsForm {

    private final SettingsStores stores;
    private final SettingsStorage storage;
    private final List<ConfigPage> configPages;
    private final ForecastPage forecast;
    private final AppPage app;
    private final AboutPage about;
    private Optional<EngineConfig> storedConfig = Optional.empty();
    private AppSettings storedApp;

    /**
     * Makes the form; nothing is read until {@link #load()}.
     *
     * @param stores where the settings are kept
     * @param configPages the pages whose settings the engine keeps, in the sidebar's order
     * @param forecast the forecast page, one of the config pages, which also holds the API keys
     * @param app the page of the app's own settings
     * @param about the About page, which shows where the API keys are kept
     */
    SettingsForm(SettingsStores stores, List<ConfigPage> configPages, ForecastPage forecast, AppPage app,
            AboutPage about) {
        this.stores = stores;
        this.storage = new SettingsStorage(stores);
        this.configPages = List.copyOf(configPages);
        this.forecast = forecast;
        this.app = app;
        this.about = about;
    }

    /** Reads every store again and shows what it holds on the pages, as the values not edited. */
    void load() {
        var keys = storage.readKeys();
        forecast.loadKeys(keys.keys(), storage.keyStorage(), keys.problem());
        try {
            var config = stores.engine().config();
            storedConfig = Optional.of(config);
            configPages.forEach(page -> page.load(config));
        } catch (ConfigProblem e) {
            storedConfig = Optional.empty();
            configPages.forEach(page -> page.showUnavailable(e.getMessage()));
        }
        storedApp = storage.readApp();
        app.load(storedApp, storage.startsAtLogin());
        about.load(storage.keyStorage());
    }

    /**
     * Whether the owner changed something since the last load or save.
     *
     * @return true when closing would throw edits away
     */
    boolean isChanged() {
        return app.isChanged() || configPages.stream().anyMatch(Page::isChanged);
    }

    /**
     * Checks and stores what the pages show, then loads them again from the stores. Nothing is stored while a value is
     * refused; see {@link SettingsStorage#save} for the order.
     *
     * @return saved, refused with the categories to look at, or failed with the system's error
     */
    SaveOutcome save() {
        var refused = new ArrayList<Category>();
        var config = storedConfig;
        if (config.isPresent()) {
            for (var page : configPages) {
                var applied = page.applyTo(config.get());
                if (applied.isPresent()) {
                    config = applied;
                } else {
                    refused.add(page.category());
                }
            }
        }
        if (!refused.isEmpty()) {
            return new SaveOutcome.Refused(refused);
        }
        var changes = new SettingsStorage.Changes(
                app.startAtLoginChanged() ? Optional.of(app.startsAtLogin()) : Optional.empty(),
                storedConfig.isPresent() && forecast.keysChanged() ? Optional.of(forecast.keys()) : Optional.empty(),
                config.equals(storedConfig) ? Optional.empty() : config,
                app.settings().equals(storedApp) ? Optional.empty() : Optional.of(app.settings()));
        var outcome = storage.save(changes, app);
        if (outcome instanceof SaveOutcome.Saved) {
            load();
        }
        return outcome;
    }
}
