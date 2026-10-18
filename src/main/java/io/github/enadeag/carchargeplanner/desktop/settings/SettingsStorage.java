package io.github.enadeag.carchargeplanner.desktop.settings;

import io.github.enadeag.carchargeplanner.desktop.app.AppSettings;
import io.github.enadeag.carchargeplanner.engine.config.ConfigProblem;
import io.github.enadeag.carchargeplanner.engine.config.EngineConfig;
import io.github.enadeag.carchargeplanner.engine.secrets.KeyChanges;
import io.github.enadeag.carchargeplanner.engine.secrets.KeyStorage;
import io.github.enadeag.carchargeplanner.engine.secrets.Secrets;
import io.github.enadeag.carchargeplanner.engine.secrets.SecretsProblem;
import java.io.IOException;
import java.util.Optional;

/**
 * Reads what Settings and set-up show from the stores, and writes what the owner chose back to them in the one order
 * both use: start at login first, as the system may refuse it; then the API keys, the engine's config and the app's
 * settings.
 *
 * <p>Reading and writing block: the keychain, the files and the system's start at login may each take a while. Call
 * them away from the JavaFX thread, through {@link io.github.enadeag.carchargeplanner.desktop.app.Background}.
 */
final class SettingsStorage {

    /**
     * What to write; each part only when it is present.
     *
     * @param startAtLogin whether the app should start at login; present when the owner changed it
     * @param keys what the owner entered and emptied in the API keys, applied to the stored ones; present when they
     *     changed
     * @param config the engine's whole config; present when it changed, or was never saved
     * @param app the app's own settings; present when they changed
     */
    record Changes(Optional<Boolean> startAtLogin, Optional<KeyChanges> keys, Optional<EngineConfig> config,
            Optional<AppSettings> app) {
    }

    /**
     * The stored API keys.
     *
     * @param keys every provider's secret settings; none when they could not be read
     * @param problem why they could not be read, in words for the owner; empty when they could
     */
    record StoredKeys(Secrets keys, Optional<String> problem) {
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
    record Stored(Optional<EngineConfig> config, Optional<String> configProblem, StoredKeys keys,
            KeyStorage keyStorage, AppSettings app, Optional<Boolean> startsAtLogin) {

        /**
         * Checks that there is a config or a reason why not.
         *
         * @throws IllegalArgumentException when both or neither are present
         */
        Stored {
            if (config.isPresent() == configProblem.isPresent()) {
                throw new IllegalArgumentException("there must be a config or the problem with it, not "
                        + (config.isPresent() ? "both" : "neither"));
            }
        }
    }

    private final SettingsStores stores;

    /**
     * Makes the storage; nothing is read yet.
     *
     * @param stores where the settings are kept
     */
    SettingsStorage(SettingsStores stores) {
        this.stores = stores;
    }

    /**
     * Reads every store. Blocks while the keychain, the files and the system answer.
     *
     * @return what they hold
     */
    Stored read() {
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
     * @return the keys, or none and why, in words for the owner, when they could not be read
     */
    StoredKeys readKeys() {
        try {
            return new StoredKeys(stores.engine().apiKeys(), Optional.empty());
        } catch (SecretsProblem e) {
            return new StoredKeys(Secrets.NONE, Optional.of(e.getMessage()));
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
    AppSettings readApp() {
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
            } catch (IOException e) {
                return false;
            }
        });
    }

    /**
     * Stops a save that waits for the owner to unlock the system keychain, such as when its window closes; that save
     * then comes to {@link SaveOutcome.KeyringLocked}. Nothing happens when no save waits.
     */
    void stopUnlocking() {
        stores.engine().stopUnlocking();
    }

    /**
     * Writes the changes, in order; when one part fails, the parts after it are not written. Blocks while the
     * system, the keychain and the files answer.
     *
     * @param changes what to write
     * @param prompting runs, on the saving thread, once the system asks the owner to unlock the keychain
     * @return saved; refused by the system's start at login, with its reason; not saved as the keychain stayed
     *     locked; or failed with the system's error when a file or the keychain could not be written
     */
    SaveOutcome save(Changes changes, Runnable prompting) {
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
                stores.engine().saveApiKeys(changes.keys().get(), prompting);
            }
            if (changes.config().isPresent()) {
                stores.engine().saveConfig(changes.config().get());
            }
            if (changes.app().isPresent()) {
                stores.appSettings().write(changes.app().get());
            }
        } catch (SecretsProblem e) {
            return e.kind() == SecretsProblem.Kind.LOCKED ? new SaveOutcome.KeyringLocked()
                    : new SaveOutcome.Failed(String.valueOf(e.getMessage()));
        } catch (IOException e) {
            return new SaveOutcome.Failed(String.valueOf(e.getMessage()));
        }
        return new SaveOutcome.Saved();
    }
}
