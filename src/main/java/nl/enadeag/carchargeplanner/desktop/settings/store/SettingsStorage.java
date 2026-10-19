package nl.enadeag.carchargeplanner.desktop.settings.store;

import nl.enadeag.carchargeplanner.desktop.app.AppSettings;
import nl.enadeag.carchargeplanner.desktop.settings.form.AllSettings;
import nl.enadeag.carchargeplanner.desktop.settings.form.SettingsChanges;
import nl.enadeag.carchargeplanner.engine.config.ConfigProblem;
import nl.enadeag.carchargeplanner.engine.config.EngineConfig;
import nl.enadeag.carchargeplanner.engine.secrets.KeyStorage;
import nl.enadeag.carchargeplanner.engine.secrets.Secrets;
import nl.enadeag.carchargeplanner.engine.secrets.SecretsProblem;
import java.io.IOException;
import java.util.Optional;

/**
 * Reads what Settings and set-up show from the stores, and writes what the owner chose back to them in the one order
 * both use: start at login first, as the system may refuse it; then the API keys, the engine's config and the app's
 * settings.
 *
 * <p>Reading and writing block: the keychain, the files and the system's start at login may each take a while. Call
 * them away from the JavaFX thread, through {@link nl.enadeag.carchargeplanner.desktop.app.Background}.
 */
public final class SettingsStorage {

    private static final System.Logger LOG = System.getLogger(SettingsStorage.class.getName());

    /**
     * The stored API keys.
     *
     * @param keys every provider's secret settings; none when they could not be read
     * @param problem the kind of problem that kept them from being read; empty when they could be read
     */
    public record StoredKeys(Secrets keys, Optional<SecretsProblem.Kind> problem) {
    }

    /**
     * Everything Settings shows, as it is stored.
     *
     * @param config the engine's config; empty when there is none that can be used
     * @param configProblem why there is no config that can be used, in words for the owner; present exactly when the
     *     config is empty
     * @param keys the stored API keys, or why they could not be read
     * @param keyStorage where the API keys are kept
     * @param app the app's own settings
     * @param startsAtLogin whether the system starts the app at login; empty when this copy of the app cannot
     */
    public record Stored(Optional<EngineConfig> config, Optional<String> configProblem, StoredKeys keys,
            KeyStorage keyStorage, AppSettings app, Optional<Boolean> startsAtLogin) {

        /**
         * Checks that there is a config or a reason why not.
         *
         * @throws IllegalArgumentException when both or neither are present
         */
        public Stored {
            if (config.isPresent() == configProblem.isPresent()) {
                throw new IllegalArgumentException("there must be a config or the problem with it, not "
                        + (config.isPresent() ? "both" : "neither"));
            }
        }

        /**
         * What is stored, to compare what is entered with.
         *
         * @return the config, the API keys, the app's own settings and start at login
         */
        public AllSettings all() {
            return new AllSettings(config, keys.keys(), app, startsAtLogin);
        }
    }

    private final SettingsStores stores;

    /**
     * Makes the storage; nothing is read yet.
     *
     * @param stores where the settings are kept
     */
    public SettingsStorage(SettingsStores stores) {
        this.stores = stores;
    }

    /**
     * Reads every store. Blocks while the keychain, the files and the system answer.
     *
     * @return what they hold
     */
    public Stored read() {
        var keys = readKeys();
        Optional<EngineConfig> config;
        Optional<String> problem;
        try {
            config = Optional.of(stores.engine().config());
            problem = Optional.empty();
        } catch (ConfigProblem e) {
            config = Optional.empty();
            problem = Optional.of(e.getMessage());
        }
        return new Stored(config, problem, keys, keyStorage(), readApp(), startsAtLogin());
    }

    /**
     * The stored API keys. Blocks while the keychain answers.
     *
     * @return the keys, or none and the kind of problem when they could not be read; the problem's own English words
     *     go to the log
     */
    StoredKeys readKeys() {
        try {
            return new StoredKeys(stores.engine().storedApiKeys(), Optional.empty());
        } catch (SecretsProblem e) {
            LOG.log(System.Logger.Level.WARNING, "The API keys could not be read for Settings: " + e.getMessage());
            return new StoredKeys(Secrets.NONE, Optional.of(e.kind()));
        }
    }

    /**
     * Where the API keys are kept; may ask the keychain whether it can be used, and then blocks.
     *
     * @return the keychain, or the private file
     */
    KeyStorage keyStorage() {
        return stores.engine().keyStorage();
    }

    /**
     * The app's own settings as the file holds them.
     *
     * @return the stored settings, or the usual ones when there is no file yet
     */
    public AppSettings readApp() {
        return stores.appSettings().read();
    }

    /**
     * Whether the system starts the app at login, asked every time; an answer it cannot give counts as off.
     *
     * @return the system's answer; empty when this copy of the app cannot start at login
     */
    Optional<Boolean> startsAtLogin() {
        return stores.startAtLogin().map(system -> {
            try {
                return system.isOn();
            } catch (IOException _) {
                return false;
            }
        });
    }

    /**
     * Stops a save that waits for the owner to unlock the system keychain, such as when its window closes; that save
     * then comes to {@link SaveOutcome.KeychainLocked}. Nothing happens when no save waits.
     */
    public void stopUnlocking() {
        stores.engine().stopUnlocking();
    }

    /**
     * Writes the changes, in order; when one part fails, the parts after it are not written. Blocks while the
     * system, the keychain and the files answer.
     *
     * @param changes what to write
     * @param prompting runs, on the saving thread, once the system asks the owner to unlock the keychain
     * @return saved; refused by the system's start at login, with its reason; not saved as the keychain stayed
     *     locked; not saved as the API keys could not be stored, whose English reason goes to the log; or failed with
     *     the system's error when a file could not be written
     */
    public SaveOutcome save(SettingsChanges changes, Runnable prompting) {
        var system = stores.startAtLogin();
        if (changes.startAtLogin().isPresent() && system.isPresent()) {
            try {
                if (changes.startAtLogin().get()) {
                    system.get().switchOn();
                } else {
                    system.get().switchOff();
                }
            } catch (IOException e) {
                return new SaveOutcome.StartAtLoginRefused(String.valueOf(e.getMessage()));
            }
        }
        try {
            if (changes.keys().isPresent()) {
                stores.engine().applyKeyChanges(changes.keys().get(), prompting);
            }
            if (changes.config().isPresent()) {
                stores.engine().saveConfig(changes.config().get());
            }
            if (changes.app().isPresent()) {
                stores.appSettings().write(changes.app().get());
            }
        } catch (SecretsProblem e) {
            if (e.kind() == SecretsProblem.Kind.LOCKED) {
                return new SaveOutcome.KeychainLocked();
            }
            LOG.log(System.Logger.Level.WARNING, "The API keys could not be saved from Settings: " + e.getMessage());
            return new SaveOutcome.KeysNotSaved();
        } catch (IOException e) {
            return new SaveOutcome.Failed(String.valueOf(e.getMessage()));
        }
        return new SaveOutcome.Saved();
    }
}
