package nl.enadeag.carchargeplanner.desktop.settings;

import static org.assertj.core.api.Assertions.assertThat;

import nl.enadeag.carchargeplanner.desktop.Fx;
import nl.enadeag.carchargeplanner.desktop.FxTest;
import nl.enadeag.carchargeplanner.desktop.Keyboard;
import nl.enadeag.carchargeplanner.desktop.Scenes;
import nl.enadeag.carchargeplanner.desktop.VisibleNames;
import nl.enadeag.carchargeplanner.desktop.settings.page.Category;
import nl.enadeag.carchargeplanner.desktop.system.PosixFilePrivacy;
import nl.enadeag.carchargeplanner.desktop.text.Language;
import nl.enadeag.carchargeplanner.desktop.text.TextKey;
import nl.enadeag.carchargeplanner.engine.config.ConfigFile;
import nl.enadeag.carchargeplanner.engine.store.PrivateFiles;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import javafx.application.ColorScheme;
import javafx.scene.Node;
import javafx.scene.input.KeyCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Settings with only a keyboard: Tab reaches every control of every category in reading order, each with a name it
 * shows and a focus ring that stands out 3:1; Enter saves, Escape closes or keeps editing, and a refused value takes
 * the focus to its category.
 */
@FxTest
class SettingsKeyboardTest {

    private static final PrivateFiles FILES = new PrivateFiles(new PosixFilePrivacy());

    private static final Clock CLOCK = Clock.systemUTC();

    @TempDir
    Path folder;

    private SettingsFixture settings;

    @AfterEach
    void close() throws Exception {
        settings.close();
    }

    private SettingsView open(ColorScheme scheme) throws Exception {
        settings = new SettingsFixture(folder, Language.ENGLISH, SettingsFixture.realProviders());
        return settings.open(true, scheme);
    }

    /** What has the focus, once the view is laid out: the control, or the row of a list. */
    private Node focused(SettingsView view) throws Exception {
        return Fx.call(() -> {
            view.applyCss();
            view.layout();
            return VisibleNames.focusTarget(view.getScene().getFocusOwner());
        });
    }

    /** The name the control with the focus shows. */
    private String focusedName(SettingsView view) throws Exception {
        var node = focused(view);
        return Fx.call(() -> VisibleNames.of(node));
    }

    @ParameterizedTest
    @EnumSource(ColorScheme.class)
    void everyCategoryIsReachedInOrderWithAShownNameAndAVisibleFocus(ColorScheme scheme) throws Exception {
        var view = open(scheme);
        // two of what a page can repeat: panel arrays and weekly away periods
        Fx.run(() -> {
            view.show(Category.SOLAR);
            settings.button(view, TextKey.PANELS_ADD).fire();
            view.show(Category.AWAY);
            view.applyCss();
            settings.button(view, TextKey.AWAY_ADD).fire();
            settings.button(view, TextKey.AWAY_ADD).fire();
        });

        var problems = new ArrayList<String>();
        for (var category : Category.values()) {
            Fx.run(() -> view.show(category));
            Keyboard.problems(view.getScene(), view).forEach(problem -> problems.add(category + ": " + problem));
        }

        assertThat(problems).isEmpty();
    }

    @Test
    void enterSavesAndTheFocusStaysWhereItWas() throws Exception {
        var view = open(ColorScheme.LIGHT);
        Fx.run(() -> {
            view.show(Category.PLANNING);
            settings.type(view, TextKey.FIELD_TARGET_CHARGE, "90");
            settings.control(view, TextKey.FIELD_TARGET_CHARGE).requestFocus();
        });

        Keyboard.press(view.getScene(), KeyCode.ENTER);

        assertThat(new ConfigFile(settings.configPath, FILES, CLOCK).read().planning().targetPct().value())
                .isEqualTo(90.0);
        assertThat(Fx.call(() -> Scenes.visibleTexts(view))).contains(settings.messages.text(TextKey.SETTINGS_SAVED));
        assertThat(focusedName(view)).isEqualTo(settings.messages.text(TextKey.FIELD_TARGET_CHARGE));
    }

    @Test
    void aRefusedValueTakesTheFocusToItsCategoryMarkedWithASign() throws Exception {
        var view = open(ColorScheme.LIGHT);
        Fx.run(() -> {
            view.show(Category.PLANNING);
            settings.type(view, TextKey.FIELD_MINIMUM_CHARGE, "95");
            settings.control(view, TextKey.FIELD_MINIMUM_CHARGE).requestFocus();
        });

        Keyboard.press(view.getScene(), KeyCode.ENTER);

        var category = focused(view);
        assertThat(Fx.call(() -> VisibleNames.of(category)))
                .isEqualTo(settings.messages.text(TextKey.CATEGORY_PLANNING));
        assertThat(Fx.call(() -> Scenes.visibleTexts(category))).as("not by colour alone").contains("!");
    }

    @Test
    void escapeClosesSettingsWithoutChanges() throws Exception {
        var view = open(ColorScheme.LIGHT);

        Keyboard.press(view.getScene(), KeyCode.ESCAPE);

        assertThat(settings.closed).isEqualTo(1);
    }

    @Test
    void escapeWithChangesAsksAndEscapeAgainKeepsEditing() throws Exception {
        var view = open(ColorScheme.LIGHT);
        Fx.run(() -> settings.type(view, TextKey.FIELD_TARGET_CHARGE, "90"));

        Keyboard.press(view.getScene(), KeyCode.ESCAPE);
        assertThat(focusedName(view)).isEqualTo(settings.messages.text(TextKey.SETTINGS_KEEP_EDITING));
        Keyboard.press(view.getScene(), KeyCode.ESCAPE);

        assertThat(settings.closed).isZero();
        assertThat(Fx.call(view::isChanged)).isTrue();
        Keyboard.press(view.getScene(), KeyCode.ESCAPE);
        assertThat(focusedName(view)).isEqualTo(settings.messages.text(TextKey.SETTINGS_KEEP_EDITING));
    }
}
