package io.github.enadeag.carchargeplanner.desktop.settings;

import io.github.enadeag.carchargeplanner.desktop.AppSettings;
import io.github.enadeag.carchargeplanner.engine.config.EngineConfig;
import io.github.enadeag.carchargeplanner.engine.secrets.KeyStorage;
import io.github.enadeag.carchargeplanner.engine.secrets.Secrets;
import io.github.enadeag.carchargeplanner.engine.secrets.SecretsProblem;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Reads what Settings and set-up show from the stores, and writes what the owner chose back to them in the one order
 * both use: start at login first, as the system may refuse it; then the API keys, the engine's config and the app's
 * settings.
 */
final class SettingsStorage {

    /**
     * What to write; each part only when it is present.
     *
     * @param startAtLogin whether the app should start at login; present when the owner changed it
     * @param keys every provider's secret settings, replacing the stored ones; present when they changed
     * @param config the engine's whole config; present when it changed, or was never saved
     * @param app the app's own settings; present when they changed
     */
    record Changes(Optional<Boolean> startAtLogin, Optional<Secrets> keys, Optional<EngineConfig> config,
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
     * The stored API keys.
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
     * Where the API keys are kept; asks the keychain whether it can be used.
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
     * Writes the changes, in order; when one part fails, the parts after it are not written.
     *
     * @param changes what to write
     * @param app the App page, which shows why the system refused start at login
     * @return saved; refused with the App category when the system refused start at login; or failed with the
     *     system's error when a file or the keychain could not be written
     */
    SaveOutcome save(Changes changes, AppPage app) {
        var system = stores.startAtLogin();
        if (changes.startAtLogin().isPresent() && system.isPresent()) {
            try {
                if (changes.startAtLogin().get()) {
                    system.get().switchOn();
                } else {
                    system.get().switchOff();
                }
            } catch (IOException e) {
                app.showStartAtLoginFailure(String.valueOf(e.getMessage()));
                return new SaveOutcome.Refused(List.of(Category.APP));
            }
        }
        try {
            if (changes.keys().isPresent()) {
                stores.engine().saveApiKeys(changes.keys().get());
            }
            if (changes.config().isPresent()) {
                stores.engine().saveConfig(changes.config().get());
            }
            if (changes.app().isPresent()) {
                stores.appSettings().write(changes.app().get());
            }
        } catch (IOException | SecretsProblem e) {
            return new SaveOutcome.Failed(String.valueOf(e.getMessage()));
        }
        return new SaveOutcome.Saved();
    }
}
