package io.github.enadeag.carchargeplanner.desktop.settings;

import io.github.enadeag.carchargeplanner.desktop.AppSettings;
import io.github.enadeag.carchargeplanner.engine.config.ConfigProblem;
import io.github.enadeag.carchargeplanner.engine.config.EngineConfig;
import io.github.enadeag.carchargeplanner.engine.secrets.Secrets;
import io.github.enadeag.carchargeplanner.engine.secrets.SecretsProblem;
import java.io.IOException;
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
        this.configPages = List.copyOf(configPages);
        this.forecast = forecast;
        this.app = app;
        this.about = about;
    }

    /** Reads every store again and shows what it holds on the pages, as the values not edited. */
    void load() {
        var storage = stores.engine().keyStorage();
        try {
            forecast.loadKeys(stores.engine().apiKeys(), storage, Optional.empty());
        } catch (SecretsProblem e) {
            forecast.loadKeys(Secrets.NONE, storage, Optional.of(e.getMessage()));
        }
        try {
            var config = stores.engine().config();
            storedConfig = Optional.of(config);
            configPages.forEach(page -> page.load(config));
        } catch (ConfigProblem e) {
            storedConfig = Optional.empty();
            configPages.forEach(page -> page.showUnavailable(e.getMessage()));
        }
        storedApp = stores.appSettings().read();
        app.load(storedApp, startsAtLogin());
        about.load(storage);
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
     * refused. Start at login comes first, as the system may refuse it; then the API keys, the engine's config and
     * the app's settings.
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
        if (!switchStartAtLogin()) {
            return new SaveOutcome.Refused(List.of(Category.APP));
        }
        try {
            if (storedConfig.isPresent() && forecast.keysChanged()) {
                stores.engine().saveApiKeys(forecast.keys());
            }
            if (!config.equals(storedConfig)) {
                stores.engine().saveConfig(config.orElseThrow());
                storedConfig = config;
            }
            if (!app.settings().equals(storedApp)) {
                stores.appSettings().write(app.settings());
            }
        } catch (IOException | SecretsProblem e) {
            return new SaveOutcome.Failed(String.valueOf(e.getMessage()));
        }
        load();
        return new SaveOutcome.Saved();
    }

    /** Asks the system for start at login when the box changed; false, with the problem shown, when it refused. */
    private boolean switchStartAtLogin() {
        var system = stores.startAtLogin();
        if (system.isEmpty() || !app.startAtLoginChanged()) {
            return true;
        }
        try {
            if (app.startsAtLogin()) {
                system.get().switchOn();
            } else {
                system.get().switchOff();
            }
            return true;
        } catch (IOException e) {
            app.showStartAtLoginFailure(String.valueOf(e.getMessage()));
            return false;
        }
    }

    /** The system's answer, read every time; one it cannot give counts as off. */
    private Optional<Boolean> startsAtLogin() {
        return stores.startAtLogin().map(system -> {
            try {
                return system.isOn();
            } catch (IOException e) {
                return false;
            }
        });
    }
}
