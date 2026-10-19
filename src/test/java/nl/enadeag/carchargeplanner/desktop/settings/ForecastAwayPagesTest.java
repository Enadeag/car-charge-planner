package nl.enadeag.carchargeplanner.desktop.settings;

import static org.assertj.core.api.Assertions.assertThat;

import nl.enadeag.carchargeplanner.LogCapture;
import nl.enadeag.carchargeplanner.desktop.Fx;
import nl.enadeag.carchargeplanner.desktop.FxTest;
import nl.enadeag.carchargeplanner.desktop.Scenes;
import nl.enadeag.carchargeplanner.desktop.settings.page.Category;
import nl.enadeag.carchargeplanner.desktop.settings.store.SettingsStorage;
import nl.enadeag.carchargeplanner.desktop.text.Language;
import nl.enadeag.carchargeplanner.desktop.text.TextKey;
import nl.enadeag.carchargeplanner.engine.away.WeeklyAwayPeriod;
import nl.enadeag.carchargeplanner.engine.config.ConfigFile;
import nl.enadeag.carchargeplanner.engine.config.EngineConfig;
import nl.enadeag.carchargeplanner.engine.config.ProviderId;
import nl.enadeag.carchargeplanner.engine.forecast.FakeProvider;
import nl.enadeag.carchargeplanner.engine.forecast.SettingFormat;
import nl.enadeag.carchargeplanner.engine.forecast.SettingSpec;
import nl.enadeag.carchargeplanner.engine.forecast.SunnyDays;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The forecast providers with their keys, the weekly away periods, and running set-up again. */
@FxTest
class ForecastAwayPagesTest {

    private static final ProviderId OPEN_METEO = new ProviderId("open-meteo");
    private static final ProviderId FORECAST_SOLAR = new ProviderId("forecast-solar");

    @TempDir
    Path folder;

    private SettingsFixture settings;

    @AfterEach
    void close() throws Exception {
        settings.close();
    }

    private static FakeProvider provider(String id, String name) {
        var settings = List.of(new SettingSpec("api_key", "API key", "", true, true));
        return new FakeProvider(FakeProvider.info(id, name, settings),
                FakeProvider.steps(SunnyDays.days(LocalDate.now(SunnyDays.TZ), 3, 4.0)));
    }

    private SettingsView open() throws Exception {
        settings = new SettingsFixture(folder, Language.ENGLISH,
                List.of(provider("open-meteo", "Open-Meteo"), provider("forecast-solar", "Forecast.Solar")));
        return settings.open();
    }

    private EngineConfig stored() throws Exception {
        return new ConfigFile(settings.configPath).read();
    }

    private <T> List<T> all(SettingsView view, Class<T> type) {
        return view.pages().stream().flatMap(page -> Scenes.all(page.view()).stream())
                .filter(type::isInstance).map(type::cast).toList();
    }

    private Button button(SettingsView view, TextKey text) {
        return all(view, Button.class).stream().filter(button -> button.getText().equals(settings.messages.text(text)))
                .findFirst().orElseThrow();
    }

    @Test
    void aKeyGoesToTheKeyStoreAndTheChoiceToTheConfigFile() throws Exception {
        var view = open();

        Fx.call(() -> {
            settings.choose(view, TextKey.FIELD_FALLBACK, Optional.empty());
            ((TextInputControl) SettingsFixture.field(view, "API key")).setText(" secret-key\n");
            return SettingsFixture.save(view);
        });

        assertThat(stored().forecast().fallback()).isEmpty();
        assertThat(settings.engine.storedApiKeys().of(OPEN_METEO)).isEqualTo(Map.of("api_key", "secret-key"));
        assertThat(Files.readString(settings.configPath)).doesNotContain("secret-key");
    }

    @Test
    void aServerAddressAKeyMayNotGoToAndAKeyWithOtherCharactersAreRefusedWhenSaved() throws Exception {
        var checked = new FakeProvider(FakeProvider.info("open-meteo", "Open-Meteo", List.of(
                        new SettingSpec("base_url", "Server address", "", true, false, SettingFormat.SERVER_ADDRESS),
                        new SettingSpec("api_key", "API key", "", true, true, SettingFormat.LETTERS_AND_DIGITS))),
                FakeProvider.steps(SunnyDays.days(LocalDate.now(SunnyDays.TZ), 3, 4.0)));
        settings = new SettingsFixture(folder, Language.ENGLISH,
                List.of(checked, provider("forecast-solar", "Forecast.Solar")));
        var view = settings.open();
        var address = (TextInputControl) SettingsFixture.field(view, "Server address");
        var key = (TextInputControl) SettingsFixture.field(view, "API key");

        boolean saved = Fx.call(() -> {
            address.setText("http://localhost@evil.example");
            key.setText("my-key/../x");
            return SettingsFixture.save(view);
        });

        assertThat(saved).isFalse();
        assertThat(Fx.call(address::getAccessibleHelp)).contains(settings.messages.text(TextKey.PROBLEM_SERVER_ADDRESS));
        assertThat(Fx.call(key::getAccessibleHelp)).contains(settings.messages.text(TextKey.PROBLEM_LETTERS_AND_DIGITS));
        assertThat(settings.engine.storedApiKeys().of(OPEN_METEO)).isEmpty();

        boolean savedAfterwards = Fx.call(() -> {
            address.setText("http://[::1]:8080/");
            key.setText("MyKey42");
            return SettingsFixture.save(view);
        });

        assertThat(savedAfterwards).isTrue();
        assertThat(settings.engine.storedApiKeys().of(OPEN_METEO)).isEqualTo(Map.of("api_key", "MyKey42"));
        assertThat(stored().forecast().settingsOf(OPEN_METEO)).containsEntry("base_url", "http://[::1]:8080/");
    }

    @Test
    void theFallbackCannotBeThePrimary() throws Exception {
        var view = open();

        boolean saved = Fx.call(() -> {
            settings.choose(view, TextKey.FIELD_PRIMARY, FORECAST_SOLAR);
            return SettingsFixture.save(view);
        });

        assertThat(saved).isFalse();
        assertThat(Fx.call(view::shown)).isEqualTo(Category.FORECAST);
        assertThat(Fx.call(() -> settings.control(view, TextKey.FIELD_FALLBACK).getAccessibleHelp()))
                .startsWith(settings.messages.text(TextKey.PROBLEM_SAME_AS_PRIMARY));
        assertThat(stored().forecast().primary()).isEqualTo(OPEN_METEO);
    }

    @Test
    void thePageSaysWhereTheKeysAreKept() throws Exception {
        var view = open();
        Fx.run(() -> view.show(Category.FORECAST));

        assertThat(Fx.call(() -> Scenes.visibleTexts(view))).contains(
                settings.messages.text(TextKey.KEYS_IN_FILE, folder.resolve("secrets.json").toString()));
    }

    @Test
    void keysThatCannotBeReadAreSaidInTheOwnersLanguageAndTheEnginesWordsGoToTheLog() throws Exception {
        Files.writeString(folder.resolve("secrets.json"), "not json");
        settings = new SettingsFixture(folder, Language.DUTCH, List.of(provider("open-meteo", "Open-Meteo")));

        try (var log = LogCapture.of(SettingsStorage.class)) {
            var view = settings.open();
            Fx.run(() -> view.show(Category.FORECAST));

            var note = settings.messages.text(TextKey.KEYS_PROBLEM) + " "
                    + settings.messages.text(TextKey.KEYS_IN_FILE, folder.resolve("secrets.json").toString());
            assertThat(Fx.call(() -> Scenes.visibleTexts(view))).contains(note);
            assertThat(log.lines()).anySatisfy(line -> assertThat(line)
                    .startsWith("WARNING The API keys could not be read for Settings: ").contains("secrets.json"));
        }
    }

    @Test
    void theTestAsksTheProviderWithWhatIsEntered() throws Exception {
        var view = open();
        Fx.run(() -> view.show(Category.FORECAST));

        Fx.run(() -> {
            ((TextInputControl) SettingsFixture.field(view, "API key")).setText("typed-key");
            all(view, Button.class).stream()
                    .filter(button -> button.getText().equals(settings.messages.text(TextKey.PROVIDER_TEST)))
                    .findFirst().orElseThrow().fire();
        });

        SettingsFixture.waitFor(() -> Scenes.visibleTexts(view).stream()
                .anyMatch(text -> text.startsWith(settings.messages.text(TextKey.PROVIDER_WORKS, 3).substring(0, 5))));
        assertThat(settings.engine.storedApiKeys().of(OPEN_METEO)).as("nothing saved").isEmpty();
    }

    @Test
    void anAddedAwayPeriodIsSaved() throws Exception {
        var view = open();

        Fx.call(() -> {
            button(view, TextKey.AWAY_ADD).fire();
            var weekdays = all(view, ComboBox.class).stream()
                    .filter(box -> SettingsFixture.named(box, settings.messages.text(TextKey.AWAY_WEEKDAY)))
                    .toList();
            @SuppressWarnings("unchecked")
            var added = (ComboBox<DayOfWeek>) weekdays.getLast();
            added.setValue(DayOfWeek.WEDNESDAY);
            times(view, TextKey.AWAY_FROM_FIELD).getLast().setText("07:30");
            times(view, TextKey.AWAY_TO_FIELD).getLast().setText("18:00");
            return SettingsFixture.save(view);
        });

        assertThat(stored().away().weekly()).containsExactly(
                new WeeklyAwayPeriod(DayOfWeek.THURSDAY, LocalTime.of(6, 0), LocalTime.of(19, 0)),
                new WeeklyAwayPeriod(DayOfWeek.WEDNESDAY, LocalTime.of(7, 30), LocalTime.of(18, 0)));
    }

    @Test
    void aPeriodThatEndsBeforeItStartsIsRefusedAndNothingIsWritten() throws Exception {
        var view = open();
        var original = Files.readString(settings.configPath);

        boolean saved = Fx.call(() -> {
            times(view, TextKey.AWAY_FROM_FIELD).getFirst().setText("20:00");
            return SettingsFixture.save(view);
        });

        assertThat(saved).isFalse();
        assertThat(settings.configPath).hasContent(original);
        assertThat(Fx.call(view::shown)).isEqualTo(Category.AWAY);
        assertThat(Fx.call(() -> times(view, TextKey.AWAY_FROM_FIELD).getFirst().getAccessibleHelp()))
                .isEqualTo(settings.messages.text(TextKey.PROBLEM_NOT_AFTER_START));
    }

    @Test
    void aTimeThatIsNotATimeIsRefused() throws Exception {
        var view = open();

        boolean saved = Fx.call(() -> {
            times(view, TextKey.AWAY_TO_FIELD).getFirst().setText("7 pm");
            return SettingsFixture.save(view);
        });

        assertThat(saved).isFalse();
        assertThat(Fx.call(() -> times(view, TextKey.AWAY_TO_FIELD).getFirst().getAccessibleHelp()))
                .isEqualTo(settings.messages.text(TextKey.PROBLEM_NOT_A_TIME));
    }

    @Test
    void removingTheOnlyPeriodLeavesTheCarHomeAllWeek() throws Exception {
        var view = open();

        Fx.call(() -> {
            button(view, TextKey.AWAY_REMOVE).fire();
            return SettingsFixture.save(view);
        });

        assertThat(stored().away().weekly()).isEmpty();
    }

    @Test
    void runningSetUpAgainAsksFirstWhenSomethingIsUnsaved() throws Exception {
        settings = new SettingsFixture(folder, Language.ENGLISH);
        var setUps = new AtomicInteger();
        var view = settings.open(true, Optional.of(setUps::incrementAndGet));

        Fx.run(() -> {
            settings.type(view, TextKey.FIELD_TARGET_CHARGE, "90");
            button(view, TextKey.RUN_SETUP).fire();
        });
        assertThat(setUps).hasValue(0);
        Fx.run(() -> settings.button(view, TextKey.SETTINGS_DISCARD).fire());

        assertThat(setUps).hasValue(1);
        assertThat(settings.closed).isEqualTo(1);
    }

    private List<TextField> times(SettingsView view, TextKey name) {
        return all(view, TextField.class).stream()
                .filter(field -> SettingsFixture.named(field, settings.messages.text(name))).toList();
    }
}
