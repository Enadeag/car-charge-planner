package nl.enadeag.carchargeplanner.desktop.settings;

import nl.enadeag.carchargeplanner.desktop.settings.about.AboutPage;
import nl.enadeag.carchargeplanner.desktop.settings.app.AppPage;
import nl.enadeag.carchargeplanner.desktop.settings.forecast.ForecastPage;
import nl.enadeag.carchargeplanner.desktop.settings.form.AllSettings;
import nl.enadeag.carchargeplanner.desktop.settings.form.SettingsChanges;
import nl.enadeag.carchargeplanner.desktop.settings.page.Category;
import nl.enadeag.carchargeplanner.desktop.settings.page.ConfigPage;
import nl.enadeag.carchargeplanner.desktop.settings.page.Page;
import nl.enadeag.carchargeplanner.desktop.settings.store.SaveOutcome;
import nl.enadeag.carchargeplanner.desktop.settings.store.SettingsStorage;
import nl.enadeag.carchargeplanner.desktop.settings.store.SettingsStores;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * {@link #read()} and {@link #write} block and run away from the JavaFX thread; the rest belongs to the JavaFX
 * thread, as it reads and changes the pages.
 */
final class SettingsForm {

    /**
     * @param stored what the stores hold after a save, read again; empty when it was not saved
     */
    record Written(SaveOutcome outcome, Optional<SettingsStorage.Stored> stored) {
    }

    private final SettingsStorage storage;
    private final List<ConfigPage> configPages;
    private final ForecastPage forecast;
    private final AppPage app;
    private final AboutPage about;
    /** What the stores held when they were shown last; null until they are. */
    private AllSettings stored;

    /**
     * Nothing is read until {@link #read()}.
     *
     * @param configPages the pages whose settings the engine keeps, in the sidebar's order
     * @param forecast the forecast page, one of the config pages, which also holds the API keys
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
     * Blocks while the keychain, the files and the system answer; may be called from any thread.
     */
    SettingsStorage.Stored read() {
        return storage.read();
    }

    void show(SettingsStorage.Stored stored) {
        var keys = stored.keys().keys();
        stored.keys().problem().ifPresentOrElse(problem -> forecast.loadKeys(keys, stored.keyStorage(), problem),
                () -> forecast.loadKeys(keys, stored.keyStorage()));
        this.stored = stored.all();
        if (stored.config().isPresent()) {
            configPages.forEach(page -> page.load(stored.config().get()));
        } else {
            configPages.forEach(page -> page.showUnavailable(stored.configProblem().orElseThrow()));
        }
        stored.startsAtLogin().ifPresentOrElse(startsAtLogin -> app.load(stored.app(), startsAtLogin),
                () -> app.loadWithoutStartAtLogin(stored.app()));
        about.show(stored.keyStorage());
    }

    boolean isChanged() {
        return app.isChanged() || configPages.stream().anyMatch(Page::isChanged);
    }

    /**
     * Problems shown before are taken away first. Pages that could not be loaded check nothing.
     *
     * @throws IllegalStateException when nothing was shown yet
     */
    List<Category> markRefused() {
        if (stored == null) {
            throw new IllegalStateException("nothing is shown yet");
        }
        var refused = new ArrayList<Category>();
        if (stored.config().isPresent()) {
            for (var page : configPages) {
                if (page.showProblems()) {
                    refused.add(page.category());
                }
            }
        }
        return List.copyOf(refused);
    }

    /**
     * What differs from what is stored, with every value as the pages show it; only reads. Call it when
     * {@link #markRefused()} found nothing.
     *
     * @throws IllegalStateException when nothing was shown yet, or a page refuses a value
     */
    SettingsChanges changes() {
        if (stored == null) {
            throw new IllegalStateException("nothing is shown yet");
        }
        var config = stored.config();
        for (var page : configPages) {
            config = config.map(entered -> page.read(entered).orElseThrow(
                    () -> new IllegalStateException(page.category() + " refuses a value; mark it first")));
        }
        var entered = new AllSettings(config, forecast.keys(), app.settings(), Optional.of(app.startsAtLogin()));
        return SettingsChanges.between(stored, entered);
    }

    /**
     * Reads the stores again after a save; see {@link SettingsStorage#save} for the order.
     * Blocks while the system, the keychain and the files answer; may be called from any thread.
     *
     * @param prompting runs, on the saving thread, once the system asks the owner to unlock the keychain
     */
    Written write(SettingsChanges changes, Runnable prompting) {
        var outcome = storage.save(changes, prompting);
        if (!(outcome instanceof SaveOutcome.Saved)) {
            return new Written(outcome, Optional.empty());
        }
        return new Written(outcome, Optional.of(storage.read()));
    }

    /**
     * Stops a write that waits for the owner to unlock the system keychain, which then comes to
     * {@link SaveOutcome.KeychainLocked}; nothing happens when none waits. Never blocks for long.
     */
    void stopUnlocking() {
        storage.stopUnlocking();
    }

    void showStartAtLoginFailure(String detail) {
        app.showStartAtLoginFailure(detail);
    }
}
