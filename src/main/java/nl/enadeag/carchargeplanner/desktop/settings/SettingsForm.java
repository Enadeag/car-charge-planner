package nl.enadeag.carchargeplanner.desktop.settings;

import nl.enadeag.carchargeplanner.desktop.settings.about.AboutPage;
import nl.enadeag.carchargeplanner.desktop.settings.app.AppPage;
import nl.enadeag.carchargeplanner.desktop.settings.calendar.CalendarPage;
import nl.enadeag.carchargeplanner.desktop.settings.forecast.ForecastPage;
import nl.enadeag.carchargeplanner.desktop.settings.form.AllSettings;
import nl.enadeag.carchargeplanner.desktop.settings.form.SettingsChanges;
import nl.enadeag.carchargeplanner.desktop.settings.page.Category;
import nl.enadeag.carchargeplanner.desktop.settings.page.ConfigPage;
import nl.enadeag.carchargeplanner.desktop.settings.page.Page;
import nl.enadeag.carchargeplanner.desktop.settings.store.SaveOutcome;
import nl.enadeag.carchargeplanner.desktop.settings.store.SettingsStorage;
import nl.enadeag.carchargeplanner.desktop.settings.store.SettingsStores;
import nl.enadeag.carchargeplanner.engine.secrets.KeyStorage;
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
    private final CalendarPage calendar;
    private final AppPage app;
    private final AboutPage about;
    /** What the stores held when they were shown last; null until they are. */
    private AllSettings stored;

    /**
     * Makes the form; nothing is read until {@link #read()}.
     *
     * @param stores where the settings are kept
     * @param configPages the pages whose settings the engine keeps, in the sidebar's order
     * @param forecast the forecast page, one of the config pages, which also holds the API keys
     * @param calendar the calendar page, one of the config pages, which also holds the calendar's consents and its
     *     secret settings
     * @param app the page of the app's own settings
     * @param about the About page, which shows where the API keys are kept
     */
    SettingsForm(SettingsStores stores, List<ConfigPage> configPages, ForecastPage forecast, CalendarPage calendar,
            AppPage app, AboutPage about) {
        this.storage = new SettingsStorage(stores);
        this.configPages = List.copyOf(configPages);
        this.forecast = forecast;
        this.calendar = calendar;
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
        var keys = stored.keys().keys();
        stored.keys().problem().ifPresentOrElse(problem -> forecast.loadKeys(keys, stored.keyStorage(), problem),
                () -> forecast.loadKeys(keys, stored.keyStorage()));
        calendar.loadStored(stored.consents(), keys, stored.keyStorage() == KeyStorage.LOCKED_KEYCHAIN);
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

    /**
     * Whether the owner changed something since the last load or save.
     *
     * @return true when closing would throw edits away
     */
    boolean isChanged() {
        return app.isChanged() || configPages.stream().anyMatch(Page::isChanged);
    }

    /**
     * Has the pages check what they show, as Save does, and marks each refused value by showing its problem at its
     * field; problems shown before are taken away first. Pages that could not be loaded check nothing.
     *
     * @return the categories with a refused value, in the sidebar's order; empty when every value is accepted
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
     * @return the changes to write
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
        var entered = new AllSettings(config, calendar.keys(forecast.keys()), app.settings(),
                Optional.of(app.startsAtLogin()), calendar.consents());
        return SettingsChanges.between(stored, entered);
    }

    /**
     * Stores the changes, and reads the stores again after a save; see {@link SettingsStorage#save} for the order.
     * Blocks while the system, the keychain and the files answer; may be called from any thread.
     *
     * @param changes what {@link #changes()} gave
     * @param prompting runs, on the saving thread, once the system asks the owner to unlock the keychain
     * @return the outcome, and what is stored after a save
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

    /**
     * Shows at start at login why the system refused it.
     *
     * @param detail the system's reason
     */
    void showStartAtLoginFailure(String detail) {
        app.showStartAtLoginFailure(detail);
    }
}
