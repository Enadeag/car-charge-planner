package io.github.enadeag.carchargeplanner.desktop.settings;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.enadeag.carchargeplanner.desktop.AppSettings;
import io.github.enadeag.carchargeplanner.desktop.Appearance;
import io.github.enadeag.carchargeplanner.desktop.DisplayMode;
import io.github.enadeag.carchargeplanner.desktop.Fx;
import io.github.enadeag.carchargeplanner.desktop.FxTest;
import io.github.enadeag.carchargeplanner.desktop.Language;
import io.github.enadeag.carchargeplanner.desktop.Scenes;
import io.github.enadeag.carchargeplanner.desktop.TextKey;
import io.github.enadeag.carchargeplanner.desktop.Units;
import io.github.enadeag.carchargeplanner.engine.config.ConfigFile;
import io.github.enadeag.carchargeplanner.engine.config.Percent;
import io.github.enadeag.carchargeplanner.engine.config.PlanningPreferences;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Hyperlink;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@FxTest
class SettingsViewTest {

    @TempDir
    Path folder;

    private SettingsFixture settings;

    @BeforeEach
    void storesInTheTestsFolder() throws Exception {
        settings = new SettingsFixture(folder, Language.ENGLISH);
    }

    @AfterEach
    void close() throws Exception {
        settings.close();
    }

    private PlanningPreferences storedPlanning() throws Exception {
        return new ConfigFile(settings.configPath).read().planning();
    }

    @Test
    void savingAChangedPreferenceWritesItAndKeepsTheRestOfTheConfig() throws Exception {
        var before = new ConfigFile(settings.configPath).read();
        var view = settings.open();

        boolean saved = Fx.call(() -> {
            settings.type(view, TextKey.FIELD_TARGET_CHARGE, "90");
            settings.type(view, TextKey.FIELD_MARGIN, "70");
            return SettingsFixture.save(view);
        });

        var after = new ConfigFile(settings.configPath).read();
        assertThat(saved).isTrue();
        assertThat(after.planning()).isEqualTo(new PlanningPreferences(new Percent(30), new Percent(90), 0.7, 0.2, 45, 15));
        assertThat(after.system()).isEqualTo(before.system());
        assertThat(after.forecast()).isEqualTo(before.forecast());
        assertThat(after.away()).isEqualTo(before.away());
        assertThat(settings.saved).isEqualTo(1);
        assertThat(settings.closed).isZero();
    }

    @Test
    void aMinimumNotBelowTheTargetIsShownAtTheMinimumAndNothingIsWritten() throws Exception {
        var original = Files.readString(settings.configPath);
        var view = settings.open();
        Fx.run(() -> view.show(Category.APP));

        var outcome = Fx.call(() -> {
            settings.type(view, TextKey.FIELD_MINIMUM_CHARGE, "85");
            settings.choose(view, TextKey.FIELD_UNITS, Units.IMPERIAL);
            return SettingsFixture.save(view);
        });

        assertThat(outcome).isFalse();
        assertThat(settings.configPath).hasContent(original);
        assertThat(settings.appSettings.path()).doesNotExist();
        assertThat(Fx.call(view::shown)).isEqualTo(Category.PLANNING);
        assertThat(Fx.call(() -> settings.control(view, TextKey.FIELD_MINIMUM_CHARGE).getAccessibleHelp()))
                .startsWith(settings.messages.text(TextKey.PROBLEM_NOT_BELOW_TARGET));
        assertThat(settings.saved).isZero();
    }

    @Test
    void aValueTheOwnerDidNotTouchKeepsItsExactValue() throws Exception {
        Files.writeString(settings.configPath, Files.readString(settings.configPath)
                .replace("\"margin\": 0.85", "\"margin\": 0.8571428").replace("\"importToleranceKwh\": 0.2",
                        "\"importToleranceKwh\": 0.123456"));
        var view = settings.open();

        Fx.call(() -> {
            settings.type(view, TextKey.FIELD_NOTIFY_LEAD, "30");
            return SettingsFixture.save(view);
        });

        assertThat(storedPlanning().margin()).isEqualTo(0.8571428);
        assertThat(storedPlanning().importToleranceKwh()).isEqualTo(0.123456);
        assertThat(storedPlanning().notifyLeadMinutes()).isEqualTo(30);
    }

    @Test
    void numbersAreTypedTheWayTheOwnersLanguageWritesThem() throws Exception {
        settings.close();
        Files.createDirectories(folder.resolve("nl"));
        settings = new SettingsFixture(folder.resolve("nl"), Language.DUTCH);
        var view = settings.open();

        Fx.call(() -> {
            settings.type(view, TextKey.FIELD_IMPORT_TOLERANCE, "0,35");
            return SettingsFixture.save(view);
        });

        assertThat(storedPlanning().importToleranceKwh()).isEqualTo(0.35);
    }

    @Test
    void savingWithoutChangesWritesNothing() throws Exception {
        var written = Files.getLastModifiedTime(settings.configPath);
        var view = settings.open();

        assertThat(Fx.call(() -> SettingsFixture.save(view))).isTrue();

        assertThat(Files.getLastModifiedTime(settings.configPath)).isEqualTo(written);
        assertThat(settings.appSettings.path()).doesNotExist();
    }

    @Test
    void theAppsOwnSettingsGoToTheirFileAndNotToTheConfig() throws Exception {
        var original = Files.readString(settings.configPath);
        var view = settings.open();

        Fx.call(() -> {
            settings.choose(view, TextKey.FIELD_UNITS, Units.IMPERIAL);
            settings.choose(view, TextKey.FIELD_MODE, DisplayMode.WINDOW);
            settings.choose(view, TextKey.FIELD_APPEARANCE, Appearance.DARK);
            settings.choose(view, TextKey.FIELD_LANGUAGE, Optional.of(Language.DUTCH));
            return SettingsFixture.save(view);
        });

        assertThat(settings.appSettings.read())
                .isEqualTo(new AppSettings(Optional.of(Language.DUTCH), Units.IMPERIAL, DisplayMode.WINDOW,
                        Appearance.DARK));
        assertThat(settings.configPath).hasContent(original);
    }

    @Test
    void startAtLoginChangesOnlyWhenSaved() throws Exception {
        var view = settings.open();

        Fx.run(() -> ((CheckBox) SettingsFixture.field(view, settings.messages.text(TextKey.FIELD_START_AT_LOGIN)))
                .setSelected(true));
        assertThat(settings.login.on).isFalse();
        Fx.call(() -> SettingsFixture.save(view));

        assertThat(settings.login.on).isTrue();
    }

    @Test
    void whenTheSystemRefusesStartAtLoginNothingIsSavedAndTheBoxSaysWhy() throws Exception {
        settings.login.failing = true;
        var original = Files.readString(settings.configPath);
        var view = settings.open();

        boolean saved = Fx.call(() -> {
            ((CheckBox) SettingsFixture.field(view, settings.messages.text(TextKey.FIELD_START_AT_LOGIN))).setSelected(true);
            settings.type(view, TextKey.FIELD_TARGET_CHARGE, "90");
            return SettingsFixture.save(view);
        });

        assertThat(saved).isFalse();
        assertThat(settings.configPath).hasContent(original);
        assertThat(Fx.call(view::shown)).isEqualTo(Category.APP);
        assertThat(Fx.call(() -> Scenes.visibleTexts(view)))
                .contains(settings.messages.text(TextKey.START_AT_LOGIN_FAILED, "access denied"));
    }

    @Test
    void outsideTheInstalledAppStartAtLoginCannotBeChosen() throws Exception {
        var view = settings.open(false);

        assertThat(Fx.call(() -> SettingsFixture.field(view, settings.messages.text(TextKey.FIELD_START_AT_LOGIN))
                .isDisabled())).isTrue();
    }

    @Test
    void cancelWithoutChangesCloses() throws Exception {
        var view = settings.open();

        Fx.run(view::cancel);

        assertThat(settings.closed).isEqualTo(1);
    }

    @Test
    void cancelWithChangesAsksFirstAndKeepEditingKeepsThem() throws Exception {
        var view = settings.open();

        Fx.run(() -> {
            settings.type(view, TextKey.FIELD_TARGET_CHARGE, "90");
            view.cancel();
        });
        assertThat(settings.closed).isZero();
        Fx.run(() -> settings.button(view, TextKey.SETTINGS_KEEP_EDITING).fire());

        assertThat(settings.closed).isZero();
        Fx.call(() -> SettingsFixture.save(view));
        assertThat(storedPlanning().targetPct()).isEqualTo(new Percent(90));
    }

    @Test
    void discardingClosesWithoutSavingAndReopeningShowsWhatIsStored() throws Exception {
        var original = Files.readString(settings.configPath);
        var view = settings.open();

        Fx.run(() -> {
            settings.type(view, TextKey.FIELD_TARGET_CHARGE, "90");
            view.cancel();
            settings.button(view, TextKey.SETTINGS_DISCARD).fire();
        });
        assertThat(settings.closed).isEqualTo(1);
        assertThat(settings.configPath).hasContent(original);

        Fx.run(view::reload);
        Fx.run(view::cancel);
        assertThat(settings.closed).as("nothing left to discard").isEqualTo(2);
    }

    @Test
    void saveAndCloseClosesOnlyWhenSaved() throws Exception {
        var view = settings.open();

        Fx.run(() -> {
            settings.type(view, TextKey.FIELD_MINIMUM_CHARGE, "95");
            settings.button(view, TextKey.SETTINGS_SAVE_CLOSE).fire();
        });
        assertThat(settings.closed).isZero();
        Fx.run(() -> {
            settings.type(view, TextKey.FIELD_MINIMUM_CHARGE, "20");
            settings.button(view, TextKey.SETTINGS_SAVE_CLOSE).fire();
        });

        assertThat(settings.closed).isEqualTo(1);
        assertThat(storedPlanning().minimumPct()).isEqualTo(new Percent(20));
    }

    @Test
    void withoutAConfigFileTheAppsOwnSettingsCanStillBeSaved() throws Exception {
        Files.delete(settings.configPath);
        var view = settings.open();

        boolean saved = Fx.call(() -> {
            settings.choose(view, TextKey.FIELD_UNITS, Units.IMPERIAL);
            return SettingsFixture.save(view);
        });

        assertThat(saved).isTrue();
        assertThat(settings.appSettings.read().units()).isEqualTo(Units.IMPERIAL);
        assertThat(settings.configPath).doesNotExist();
    }

    @Test
    void aboutShowsTheRequiredAttributionAndTheVersion() throws Exception {
        var view = settings.open();
        Fx.run(() -> view.show(Category.ABOUT));

        assertThat(Fx.call(() -> Scenes.visibleTexts(view))).contains("Car charge planner, originally by Kelvin Vroegop",
                settings.messages.text(TextKey.ABOUT_VERSION, "1.2.3"));
    }

    @Test
    void aboutOpensTheLicenceAndTheSourceInTheBrowser() throws Exception {
        var view = settings.open();
        Fx.run(() -> view.show(Category.ABOUT));

        Fx.run(() -> Scenes.all(view).stream().filter(Hyperlink.class::isInstance).map(Hyperlink.class::cast)
                .forEach(Hyperlink::fire));

        assertThat(settings.opened).containsExactly(URI.create("https://www.gnu.org/licenses/gpl-3.0.html"),
                URI.create("https://github.com/Enadeag/car-charge-planner"));
    }

    @Test
    void aboutSaysWhereTheFilesAreKept() throws Exception {
        var view = settings.open();
        Fx.run(() -> view.show(Category.ABOUT));

        var paths = Fx.call(() -> Scenes.all(view).stream()
                .filter(node -> node instanceof javafx.scene.control.TextField field && !field.isEditable())
                .map(node -> ((javafx.scene.control.TextField) node).getText()).toList());
        assertThat(paths).containsExactly(settings.configPath.toString(), settings.appSettings.path().toString(),
                folder.resolve("data").toString(), folder.resolve("data").resolve("app.0.log").toString(),
                settings.messages.text(TextKey.ABOUT_KEYS_FILE, folder.resolve("secrets.json").toString()));
    }
}
