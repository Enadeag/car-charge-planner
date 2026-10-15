package io.github.enadeag.carchargeplanner.desktop.settings;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.enadeag.carchargeplanner.desktop.Fx;
import io.github.enadeag.carchargeplanner.desktop.Language;
import io.github.enadeag.carchargeplanner.desktop.Scenes;
import io.github.enadeag.carchargeplanner.desktop.TextKey;
import io.github.enadeag.carchargeplanner.engine.away.RecurringAwayPeriod;
import io.github.enadeag.carchargeplanner.engine.config.ConfigFile;
import io.github.enadeag.carchargeplanner.engine.config.EngineConfig;
import io.github.enadeag.carchargeplanner.engine.config.ProviderId;
import io.github.enadeag.carchargeplanner.engine.forecast.FakeProvider;
import io.github.enadeag.carchargeplanner.engine.forecast.SettingSpec;
import io.github.enadeag.carchargeplanner.engine.forecast.SunnyDays;
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
            ((TextInputControl) settings.field(view, "API key")).setText(" secret-key\n");
            return view.save();
        });

        assertThat(stored().forecast().fallback()).isEmpty();
        assertThat(settings.engine.apiKeys().of(OPEN_METEO)).isEqualTo(Map.of("api_key", "secret-key"));
        assertThat(Files.readString(settings.configPath)).doesNotContain("secret-key");
    }

    @Test
    void theFallbackCannotBeThePrimary() throws Exception {
        var view = open();

        boolean saved = Fx.call(() -> {
            settings.choose(view, TextKey.FIELD_PRIMARY, FORECAST_SOLAR);
            return view.save();
        });

        assertThat(saved).isFalse();
        assertThat(Fx.call(view::shown)).isEqualTo(Category.FORECAST);
        assertThat(Fx.call(() -> settings.field(view, TextKey.FIELD_FALLBACK).getAccessibleHelp()))
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
    void theTestAsksTheProviderWithWhatIsEntered() throws Exception {
        var view = open();
        Fx.run(() -> view.show(Category.FORECAST));

        Fx.run(() -> {
            ((TextInputControl) settings.field(view, "API key")).setText("typed-key");
            all(view, Button.class).stream()
                    .filter(button -> button.getText().equals(settings.messages.text(TextKey.PROVIDER_TEST)))
                    .findFirst().orElseThrow().fire();
        });

        SettingsFixture.waitFor(() -> Scenes.visibleTexts(view).stream()
                .anyMatch(text -> text.startsWith(settings.messages.text(TextKey.PROVIDER_WORKS, 3).substring(0, 5))));
        assertThat(settings.engine.apiKeys().of(OPEN_METEO)).as("nothing saved").isEmpty();
    }

    @Test
    void anAddedAwayPeriodIsSaved() throws Exception {
        var view = open();

        Fx.call(() -> {
            button(view, TextKey.AWAY_ADD).fire();
            var weekdays = all(view, ComboBox.class).stream()
                    .filter(box -> settings.messages.text(TextKey.AWAY_WEEKDAY).equals(box.getAccessibleText()))
                    .toList();
            @SuppressWarnings("unchecked")
            var added = (ComboBox<DayOfWeek>) weekdays.getLast();
            added.setValue(DayOfWeek.WEDNESDAY);
            times(view, TextKey.AWAY_FROM_FIELD).getLast().setText("07:30");
            times(view, TextKey.AWAY_TO_FIELD).getLast().setText("18:00");
            return view.save();
        });

        assertThat(stored().away().recurring()).containsExactly(
                new RecurringAwayPeriod(DayOfWeek.THURSDAY, LocalTime.of(6, 0), LocalTime.of(19, 0)),
                new RecurringAwayPeriod(DayOfWeek.WEDNESDAY, LocalTime.of(7, 30), LocalTime.of(18, 0)));
    }

    @Test
    void aPeriodThatEndsBeforeItStartsIsRefusedAndNothingIsWritten() throws Exception {
        var view = open();
        var original = Files.readString(settings.configPath);

        boolean saved = Fx.call(() -> {
            times(view, TextKey.AWAY_FROM_FIELD).getFirst().setText("20:00");
            return view.save();
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
            return view.save();
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
            return view.save();
        });

        assertThat(stored().away().recurring()).isEmpty();
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
                .filter(field -> settings.messages.text(name).equals(field.getAccessibleText())).toList();
    }
}
