package nl.enadeag.carchargeplanner.desktop.settings;

import static org.assertj.core.api.Assertions.assertThat;

import nl.enadeag.carchargeplanner.desktop.Fx;
import nl.enadeag.carchargeplanner.desktop.FxTest;
import nl.enadeag.carchargeplanner.desktop.Scenes;
import nl.enadeag.carchargeplanner.desktop.app.Background;
import nl.enadeag.carchargeplanner.desktop.settings.page.Category;
import nl.enadeag.carchargeplanner.desktop.settings.store.SaveOutcome;
import nl.enadeag.carchargeplanner.desktop.text.Language;
import nl.enadeag.carchargeplanner.desktop.text.TextKey;
import nl.enadeag.carchargeplanner.engine.config.ConfigFile;
import nl.enadeag.carchargeplanner.engine.config.ProviderId;
import nl.enadeag.carchargeplanner.engine.forecast.FakeProvider;
import nl.enadeag.carchargeplanner.engine.forecast.SettingSpec;
import nl.enadeag.carchargeplanner.engine.forecast.SunnyDays;
import nl.enadeag.carchargeplanner.engine.secrets.ApiKeys;
import nl.enadeag.carchargeplanner.engine.secrets.KeychainException;
import nl.enadeag.carchargeplanner.engine.secrets.LockableKeychain;
import nl.enadeag.carchargeplanner.engine.secrets.Secrets;
import nl.enadeag.carchargeplanner.engine.secrets.SecretsFile;
import nl.enadeag.carchargeplanner.engine.secrets.UnlockOutcome;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import javafx.scene.control.Button;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Settings while the keyring that holds the API keys is locked, as after a login that did not unlock it: the pages say
 * so, and Save has the system ask the owner to unlock it, with the file only when the owner dismisses that.
 */
@FxTest
class LockedKeyringTest {

    private static final ProviderId OPEN_METEO = new ProviderId("open-meteo");
    private static final ProviderId FORECAST_SOLAR = new ProviderId("forecast-solar");

    @TempDir
    Path folder;

    private final LockableKeychain keychain = new LockableKeychain();
    private SettingsFixture settings;

    /** Both providers' keys, stored before the keyring was locked. */
    private static final Secrets STORED = new Secrets(Map.of(OPEN_METEO, Map.of("api_key", "OM-STORED"),
            FORECAST_SOLAR, Map.of("api_key", "FS-STORED")));

    @BeforeEach
    void storeKeysAndLockTheKeyring() throws Exception {
        new ApiKeys(keychain, new SecretsFile(folder.resolve("unused.json"))).save(STORED);
        keychain.locked = true;
    }

    @AfterEach
    void close() throws Exception {
        keychain.stopUnlocking();
        settings.close();
    }

    private static FakeProvider provider(String id, String name) {
        var settings = List.of(new SettingSpec("api_key", "API key", "", true, true));
        return new FakeProvider(FakeProvider.info(id, name, settings),
                FakeProvider.steps(SunnyDays.days(LocalDate.now(SunnyDays.TZ), 3, 4.0)));
    }

    private SettingsView open(Background background) throws Exception {
        settings = new SettingsFixture(folder, Language.ENGLISH,
                List.of(provider("open-meteo", "Open-Meteo"), provider("forecast-solar", "Forecast.Solar")), keychain,
                background);
        return settings.open();
    }

    private SettingsView open() throws Exception {
        return open(new Background(Runnable::run, Runnable::run));
    }

    /** The locked fields' buttons to enter a new key, in the order the cards show. */
    private List<Button> replaceButtons(SettingsView view) {
        return view.pages().stream().flatMap(page -> Scenes.all(page.view()).stream())
                .filter(node -> node instanceof Button button
                        && button.getText().equals(settings.messages.text(TextKey.SECRET_REPLACE)))
                .map(Button.class::cast).filter(Button::isVisible).toList();
    }

    /** Chooses to enter a new Open-Meteo key, as the owner does, and types it. */
    private void typeAKey(SettingsView view) {
        replaceButtons(view).getFirst().fire();
        ((TextInputControl) SettingsFixture.field(view, "API key")).setText("NEW-KEY");
    }

    /** The keys as stored, read once the keyring is unlocked again. */
    private Secrets storedAfterUnlocking() throws Exception {
        keychain.locked = false;
        var keys = new ApiKeys(keychain, new SecretsFile(folder.resolve("secrets.json")));
        keys.moveFileIntoKeychain(); // as the engine's next round does
        return keys.load();
    }

    private List<String> texts(SettingsView view) throws Exception {
        return Fx.call(() -> Scenes.visibleTexts(view));
    }

    @Test
    void theForecastAndAboutPagesSayTheKeyringIsLocked() throws Exception {
        var view = open();

        Fx.run(() -> view.show(Category.FORECAST));
        assertThat(texts(view)).contains(settings.messages.text(TextKey.KEYS_IN_LOCKED_KEYCHAIN))
                .noneMatch(text -> text.contains("could not be read"));

        Fx.run(() -> view.show(Category.ABOUT));
        assertThat(Fx.call(() -> Scenes.all(view).stream().filter(TextField.class::isInstance)
                .map(field -> ((TextField) field).getText()).toList()))
                .contains(settings.messages.text(TextKey.ABOUT_KEYS_LOCKED));
    }

    @Test
    void aKeySavedWhileLockedGoesIntoTheKeyringOnceTheOwnerUnlocksIt() throws Exception {
        var view = open();

        assertThat(Fx.call(() -> {
            typeAKey(view);
            return SettingsFixture.save(view);
        })).isTrue();

        assertThat(keychain.prompts).hasValue(1);
        assertThat(settings.engine.storedApiKeys()).as("the other provider's key stays").isEqualTo(new Secrets(Map.of(
                OPEN_METEO, Map.of("api_key", "NEW-KEY"), FORECAST_SOLAR, Map.of("api_key", "FS-STORED"))));
        assertThat(folder.resolve("secrets.json")).doesNotExist();
        Fx.run(() -> view.show(Category.FORECAST));
        assertThat(texts(view)).contains(settings.messages.text(TextKey.KEYS_IN_KEYCHAIN));
    }

    @Test
    void whenTheOwnerDismissesThePromptTheKeyGoesToTheFileAndThePageSaysSo() throws Exception {
        keychain.answer = UnlockOutcome.DISMISSED;
        var view = open();

        assertThat(Fx.call(() -> {
            typeAKey(view);
            return SettingsFixture.save(view);
        })).isTrue();

        assertThat(new SecretsFile(folder.resolve("secrets.json")).path()).exists();
        assertThat(storedAfterUnlocking()).as("moved in over the keyring's other key").isEqualTo(new Secrets(Map.of(
                OPEN_METEO, Map.of("api_key", "NEW-KEY"), FORECAST_SOLAR, Map.of("api_key", "FS-STORED"))));
        keychain.locked = true;
        Fx.run(() -> view.show(Category.FORECAST));
        assertThat(texts(view)).contains(
                settings.messages.text(TextKey.KEYS_IN_FILE, folder.resolve("secrets.json").toString()));
    }

    @Test
    void whenTheKeyringCannotBeUnlockedNothingIsSavedAndTheOwnerIsToldToUnlockItAndSaveAgain() throws Exception {
        keychain.unlockFails = KeychainException.Kind.REFUSED;
        var view = open();
        var before = new ConfigFile(settings.configPath).read();

        Fx.run(() -> {
            typeAKey(view);
            settings.choose(view, TextKey.FIELD_FALLBACK, Optional.empty());
            view.save(() -> { });
        });

        assertThat(Fx.call(view::outcome)).contains(new SaveOutcome.KeyringLocked());
        assertThat(texts(view)).contains(settings.messages.text(TextKey.SETTINGS_KEYRING_LOCKED));
        assertThat(new ConfigFile(settings.configPath).read()).as("nothing after the keys").isEqualTo(before);
        assertThat(folder.resolve("secrets.json")).doesNotExist();
        assertThat(storedAfterUnlocking()).isEqualTo(STORED);
        assertThat(Fx.call(view::isChanged)).as("the edits stay, to save again").isTrue();
    }

    @Test
    void closingWhileSaveWaitsForTheOwnerStopsTheWaitAndKeepsTheWindowOpen() throws Exception {
        keychain.answer = null;
        var view = open(VirtualThreads.background());
        SettingsFixture.waitFor(() -> !view.isReading());

        Fx.run(() -> {
            typeAKey(view);
            view.save(() -> { });
        });
        assertThat(keychain.prompting.await(10, TimeUnit.SECONDS)).as("the prompt shows").isTrue();
        SettingsFixture.waitFor(() -> Scenes.visibleTexts(view)
                .contains(settings.messages.text(TextKey.SETTINGS_SAVING_UNLOCK)));
        assertThat(Fx.call(() -> view.lookup(".busy").isFocused())).as("read out").isTrue();

        Fx.run(view::cancel);

        SettingsFixture.waitFor(() -> view.outcome().isPresent());
        assertThat(Fx.call(view::outcome)).contains(new SaveOutcome.KeyringLocked());
        assertThat(settings.closed).as("the owner sees why nothing was saved").isZero();
        assertThat(folder.resolve("secrets.json")).doesNotExist();
    }

    @Test
    void keyFieldsThatCouldNotBeReadAreLockedAndSayWhy() throws Exception {
        var view = open();
        Fx.run(() -> view.show(Category.FORECAST));

        var fields = Fx.call(() -> view.pages().stream().flatMap(page -> Scenes.all(page.view()).stream())
                .filter(PasswordField.class::isInstance).map(PasswordField.class::cast).toList());
        assertThat(fields).isNotEmpty().allSatisfy(field -> {
            assertThat(field.isDisabled()).isTrue();
            assertThat(field.getText()).isEmpty();
            assertThat(field.getPromptText()).isEqualTo(settings.messages.text(TextKey.SECRET_LOCKED));
        });
        assertThat(Fx.call(() -> replaceButtons(view).getFirst().getAccessibleText())).isEqualTo(
                settings.messages.text(TextKey.SECRET_LOCKED_SPOKEN, "API key (Open-Meteo)"));
        assertThat(Fx.call(view::isChanged)).isFalse();
    }

    @Test
    void savingOtherSettingsWhileLockedLeavesTheKeysAloneAndAsksForNoPrompt() throws Exception {
        var view = open();

        assertThat(Fx.call(() -> {
            settings.choose(view, TextKey.FIELD_FALLBACK, Optional.empty());
            return SettingsFixture.save(view);
        })).isTrue();

        assertThat(keychain.prompts).hasValue(0);
        assertThat(storedAfterUnlocking()).isEqualTo(STORED);
    }
}
