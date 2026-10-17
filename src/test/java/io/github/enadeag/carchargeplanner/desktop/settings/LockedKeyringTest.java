package io.github.enadeag.carchargeplanner.desktop.settings;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.enadeag.carchargeplanner.desktop.Background;
import io.github.enadeag.carchargeplanner.desktop.Fx;
import io.github.enadeag.carchargeplanner.desktop.FxTest;
import io.github.enadeag.carchargeplanner.desktop.Language;
import io.github.enadeag.carchargeplanner.desktop.Scenes;
import io.github.enadeag.carchargeplanner.desktop.TextKey;
import io.github.enadeag.carchargeplanner.engine.config.ConfigFile;
import io.github.enadeag.carchargeplanner.engine.config.ProviderId;
import io.github.enadeag.carchargeplanner.engine.forecast.FakeProvider;
import io.github.enadeag.carchargeplanner.engine.forecast.SettingSpec;
import io.github.enadeag.carchargeplanner.engine.forecast.SunnyDays;
import io.github.enadeag.carchargeplanner.engine.secrets.KeychainException;
import io.github.enadeag.carchargeplanner.engine.secrets.LockableKeychain;
import io.github.enadeag.carchargeplanner.engine.secrets.UnlockOutcome;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
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

    @TempDir
    Path folder;

    private final LockableKeychain keychain = new LockableKeychain();
    private SettingsFixture settings;

    @BeforeEach
    void lockTheKeyring() {
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

    private void typeAKey(SettingsView view) {
        ((TextInputControl) SettingsFixture.field(view, "API key")).setText("NEW-KEY");
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
        assertThat(settings.engine.apiKeys().of(OPEN_METEO)).isEqualTo(Map.of("api_key", "NEW-KEY"));
        assertThat(folder.resolve("secrets.json")).doesNotExist();
        Fx.run(() -> view.show(Category.FORECAST));
        assertThat(texts(view)).contains(settings.messages.text(TextKey.KEYS_IN_KEYCHAIN));
    }

    @Test
    void whenTheOwnerDismissesThePromptTheKeyGoesToTheFileAndThePageSaysSo() throws Exception {
        keychain.answer = Optional.of(UnlockOutcome.DISMISSED);
        var view = open();

        assertThat(Fx.call(() -> {
            typeAKey(view);
            return SettingsFixture.save(view);
        })).isTrue();

        assertThat(folder.resolve("secrets.json")).exists();
        Fx.run(() -> view.show(Category.FORECAST));
        assertThat(texts(view)).contains(
                settings.messages.text(TextKey.KEYS_IN_FILE, folder.resolve("secrets.json").toString()));
    }

    @Test
    void whenTheKeyringCannotBeUnlockedNothingIsSavedAndTheOwnerIsToldToUnlockItAndSaveAgain() throws Exception {
        keychain.unlockFails = Optional.of(KeychainException.Kind.REFUSED);
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
        assertThat(keychain.entries).isEmpty();
        assertThat(Fx.call(view::isChanged)).as("the edits stay, to save again").isTrue();
    }

    @Test
    void closingWhileSaveWaitsForTheOwnerStopsTheWaitAndKeepsTheWindowOpen() throws Exception {
        keychain.answer = Optional.empty();
        var view = open(Background.onVirtualThreads());
        SettingsFixture.waitFor(() -> !view.isReading());

        Fx.run(() -> {
            typeAKey(view);
            view.save(() -> { });
        });
        assertThat(keychain.prompting.await(10, TimeUnit.SECONDS)).as("the prompt shows").isTrue();
        assertThat(texts(view)).contains(settings.messages.text(TextKey.SETTINGS_SAVING));

        Fx.run(view::cancel);

        SettingsFixture.waitFor(() -> view.outcome().isPresent());
        assertThat(Fx.call(view::outcome)).contains(new SaveOutcome.KeyringLocked());
        assertThat(settings.closed).as("the owner sees why nothing was saved").isZero();
        assertThat(folder.resolve("secrets.json")).doesNotExist();
    }
}
