package nl.enadeag.carchargeplanner.desktop.settings;

import static org.assertj.core.api.Assertions.assertThat;

import nl.enadeag.carchargeplanner.desktop.AccessibleNames;
import nl.enadeag.carchargeplanner.desktop.Fx;
import nl.enadeag.carchargeplanner.desktop.FxTest;
import nl.enadeag.carchargeplanner.desktop.Keyboard;
import nl.enadeag.carchargeplanner.desktop.Scenes;
import nl.enadeag.carchargeplanner.desktop.settings.page.Category;
import nl.enadeag.carchargeplanner.desktop.text.Language;
import nl.enadeag.carchargeplanner.desktop.text.TextKey;
import nl.enadeag.carchargeplanner.engine.config.ConfigFile;
import java.nio.file.Path;
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
 * Settings with only a keyboard, and with a screen reader on Windows: Tab reaches every control of every category in
 * reading order, each with a name of its own and a focus ring that stands out 3:1; Enter saves, Escape closes or
 * keeps editing, and what a Save says gets the focus.
 */
@FxTest
class SettingsKeyboardTest {

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

    /** The control a screen reader calls by this name, which takes the focus, on the category shown. */
    private Node focusable(SettingsView view, TextKey name) {
        view.applyCss();
        return Scenes.all(view).stream().filter(Scenes::reallyVisible)
                .filter(node -> settings.messages.text(name).equals(node.getAccessibleText()))
                .findFirst().orElseThrow();
    }

    /** What a screen reader reads at the focus, once the view is laid out, as it is by the time the reader asks. */
    private String focusedName(SettingsView view) throws Exception {
        return Fx.call(() -> {
            view.applyCss();
            view.layout();
            return AccessibleNames.of(AccessibleNames.focusTarget(view.getScene().getFocusOwner()));
        });
    }

    @ParameterizedTest
    @EnumSource(ColorScheme.class)
    void everyCategoryIsReachedInOrderWithNamesOfTheirOwnAndAVisibleFocus(ColorScheme scheme) throws Exception {
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
    void enterSavesAndTheFocusGoesToWhatTheSaveSays() throws Exception {
        var view = open(ColorScheme.LIGHT);
        Fx.run(() -> {
            view.show(Category.PLANNING);
            settings.type(view, TextKey.FIELD_TARGET_CHARGE, "90");
            focusable(view, TextKey.FIELD_TARGET_CHARGE).requestFocus();
        });

        Keyboard.press(view.getScene(), KeyCode.ENTER);

        assertThat(new ConfigFile(settings.configPath).read().planning().targetPct().value()).isEqualTo(90.0);
        assertThat(focusedName(view)).isEqualTo(settings.messages.text(TextKey.SETTINGS_SAVED));
    }

    @Test
    void aRefusedValueTakesTheFocusToItsCategoryWhichSaysSo() throws Exception {
        var view = open(ColorScheme.LIGHT);
        Fx.run(() -> {
            view.show(Category.PLANNING);
            settings.type(view, TextKey.FIELD_MINIMUM_CHARGE, "95");
            focusable(view, TextKey.FIELD_MINIMUM_CHARGE).requestFocus();
        });

        Keyboard.press(view.getScene(), KeyCode.ENTER);

        assertThat(focusedName(view)).isEqualTo(settings.messages.text(TextKey.CATEGORY_HAS_PROBLEMS,
                settings.messages.text(TextKey.CATEGORY_PLANNING)));
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
