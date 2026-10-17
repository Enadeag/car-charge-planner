package io.github.enadeag.carchargeplanner.desktop.settings;

import io.github.enadeag.carchargeplanner.desktop.AppSettings;
import io.github.enadeag.carchargeplanner.engine.config.EngineConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * What Settings shows against what is stored: it shows the stores' contents on the pages, tells whether something
 * changed, and on Save has the pages check what they show, then the engine check and keep its part, the system its
 * start at login, and the app settings file the rest.
 *
 * <p>{@link #read()} and {@link #write} block and run away from the JavaFX thread; the rest belongs to the JavaFX
 * thread, as it reads and changes the pages.
 */
final class SettingsForm {

    /** What the pages hold when Save is pressed. */
    sealed interface Entered {

        /**
         * A value was refused; each shows its problem next to its field.
         *
         * @param categories the categories with a refused value, in the sidebar's order; at least one
         */
        record Refused(List<Category> categories) implements Entered {

            /** Copies the list. */
            public Refused {
                categories = List.copyOf(categories);
            }
        }

        /**
         * Every value was accepted.
         *
         * @param changes what differs from what is stored, to write
         */
        record Accepted(SettingsStorage.Changes changes) implements Entered {
        }
    }

    /**
     * What a write came to.
     *
     * @param outcome saved, refused by the system, or failed
     * @param stored what the stores hold after a save, read again; empty when it was not saved
     */
    record Written(SaveOutcome outcome, Optional<SettingsStorage.Stored> stored) {
    }

    private final SettingsStorage storage;
    private final List<ConfigPage> configPages;
    private final ForecastPage forecast;
    private final AppPage app;
    private final AboutPage about;
    private Optional<EngineConfig> storedConfig = Optional.empty();
    private Optional<AppSettings> storedApp = Optional.empty();

    /**
     * Makes the form; nothing is read until {@link #read()}.
     *
     * @param stores where the settings are kept
     * @param configPages the pages whose settings the engine keeps, in the sidebar's order
     * @param forecast the forecast page, one of the config pages, which also holds the API keys
     * @param app the page of the app's own settings
     * @param about the About page, which shows where the API keys are kept
     */
    SettingsForm(SettingsStores stores, List<ConfigPage> configPages, ForecastPage forecast, AppPage app,
            AboutPage about) {
        this.storage = new SettingsStorage(stores);
        this.configPages = List.copyOf(configPages);
        this.forecast = forecast;
        this.app = app;
        this.about = about;
    }

    /**
     * Reads every store. Blocks while the keychain, the files and the system answer; may be called from any thread.
     *
     * @return what the stores hold
     */
    SettingsStorage.Stored read() {
        return storage.read();
    }

    /**
     * Shows what the stores hold on the pages, as the values not edited.
     *
     * @param stored what {@link #read()} or {@link #write} read
     */
    void show(SettingsStorage.Stored stored) {
        forecast.loadKeys(stored.keys().keys(), stored.keyStorage(), stored.keys().problem());
        storedConfig = stored.config();
        if (stored.config().isPresent()) {
            configPages.forEach(page -> page.load(stored.config().get()));
        } else {
            configPages.forEach(page -> page.showUnavailable(stored.configProblem().orElseThrow()));
        }
        storedApp = Optional.of(stored.app());
        app.load(stored.app(), stored.startsAtLogin());
        about.load(stored.keyStorage());
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
     * Has the pages check what they show, as Save does; a refused value shows its problem at its field.
     *
     * @return the categories with a refused value, or what differs from what is stored
     */
    Entered enter() {
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
            return new Entered.Refused(refused);
        }
        return new Entered.Accepted(new SettingsStorage.Changes(
                app.startAtLoginChanged() ? Optional.of(app.startsAtLogin()) : Optional.empty(),
                storedConfig.isPresent() && forecast.keysChanged() ? Optional.of(forecast.keys()) : Optional.empty(),
                config.equals(storedConfig) ? Optional.empty() : config,
                Optional.of(app.settings()).equals(storedApp) ? Optional.empty() : Optional.of(app.settings())));
    }

    /**
     * Stores the changes, and reads the stores again after a save; see {@link SettingsStorage#save} for the order.
     * Blocks while the system, the keychain and the files answer; may be called from any thread.
     *
     * @param changes what {@link #enter()} accepted
     * @return the outcome, and what is stored after a save
     */
    Written write(SettingsStorage.Changes changes) {
        var outcome = storage.save(changes);
        if (!(outcome instanceof SaveOutcome.Saved)) {
            return new Written(outcome, Optional.empty());
        }
        return new Written(outcome, Optional.of(storage.read()));
    }

    /**
     * Shows at start at login why the system refused it.
     *
     * @param detail the system's reason
     */
    void showStartAtLoginFailure(String detail) {
        app.showStartAtLoginFailure(detail);
    }
}
