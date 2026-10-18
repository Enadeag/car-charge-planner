package io.github.enadeag.carchargeplanner.desktop.settings;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.enadeag.carchargeplanner.desktop.AccessibleNames;
import io.github.enadeag.carchargeplanner.desktop.Fx;
import io.github.enadeag.carchargeplanner.desktop.FxTest;
import io.github.enadeag.carchargeplanner.desktop.Keyboard;
import io.github.enadeag.carchargeplanner.desktop.text.Language;
import io.github.enadeag.carchargeplanner.desktop.text.TextKey;
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
 * Set-up with only a keyboard, and with a screen reader on Windows: Tab reaches every control of every step in
 * reading order, each with a name of its own and a focus ring that stands out 3:1; Enter goes on, Escape asks before
 * leaving or keeps going, and a refused value gets the focus with its problem.
 */
@FxTest
class SetUpKeyboardTest {

    @TempDir
    Path folder;

    private SetUpFixture setUp;

    @AfterEach
    void close() throws Exception {
        setUp.close();
    }

    private SetUpView open(ColorScheme scheme) throws Exception {
        setUp = new SetUpFixture(folder, Language.ENGLISH);
        return setUp.open(scheme);
    }

    private static Node focused(SetUpView view) throws Exception {
        return Fx.call(() -> AccessibleNames.focusTarget(view.getScene().getFocusOwner()));
    }

    @ParameterizedTest
    @EnumSource(ColorScheme.class)
    void everyStepIsReachedInOrderWithNamesOfTheirOwnAndAVisibleFocus(ColorScheme scheme) throws Exception {
        var view = open(scheme);
        var problems = new ArrayList<String>();
        for (var step : SetUpStep.values()) {
            Fx.run(() -> {
                view.show(step);
                var tabs = view.lookup(".day-tabs"); // the preview's; a growing tab is narrow on purpose
                if (tabs != null) {
                    tabs.setStyle("-fx-open-tab-animation: none;");
                }
            });
            switch (step) {
                case SOLAR -> Fx.run(() -> setUp.button(view, TextKey.PANELS_ADD).fire());
                case MORE -> Fx.run(() -> setUp.button(view, TextKey.AWAY_ADD).fire());
                case CHECK -> SettingsFixture.waitFor(() -> !setUp.button(view, TextKey.SETUP_CHECK_AGAIN).isDisabled());
                case PREVIEW -> setUp.awaitPreview(view);
                default -> { }
            }
            Keyboard.problems(view.getScene(), view).forEach(problem -> problems.add(step + ": " + problem));
        }

        assertThat(problems).isEmpty();
    }

    @Test
    void enterInAFieldGoesToTheNextStep() throws Exception {
        var view = open(ColorScheme.LIGHT);
        Fx.run(() -> view.show(SetUpStep.CAR)); // the focus is in its first field

        Keyboard.press(view.getScene(), KeyCode.ENTER);

        assertThat(Fx.call(view::step)).isEqualTo(SetUpStep.CHARGER);
    }

    @Test
    void escapeAsksBeforeLeavingAndEscapeAgainKeepsGoing() throws Exception {
        var view = open(ColorScheme.LIGHT);
        Fx.run(() -> view.show(SetUpStep.CHARGER));

        Keyboard.press(view.getScene(), KeyCode.ESCAPE);
        assertThat(Fx.call(view::isAsking)).isTrue();
        Keyboard.press(view.getScene(), KeyCode.ESCAPE);

        assertThat(Fx.call(view::isAsking)).isFalse();
        assertThat(setUp.closed).isZero();
        assertThat(Fx.call(view::step)).isEqualTo(SetUpStep.CHARGER);
    }

    @Test
    void aRefusedValueTakesTheFocusToItsFieldWhereAScreenReaderReadsTheProblemFirst() throws Exception {
        var view = open(ColorScheme.LIGHT);
        Fx.run(() -> {
            view.show(SetUpStep.CHARGER);
            setUp.type(view, TextKey.FIELD_MIN_CURRENT, "10");
            setUp.type(view, TextKey.FIELD_MAX_CURRENT, "8");
        });

        Keyboard.press(view.getScene(), KeyCode.ENTER);

        var field = focused(view);
        assertThat(Fx.call(view::step)).isEqualTo(SetUpStep.CHARGER);
        assertThat(Fx.call(() -> AccessibleNames.of(field))).isEqualTo(setUp.messages.text(TextKey.FIELD_MAX_CURRENT));
        assertThat(Fx.call(() -> AccessibleNames.help(field)))
                .startsWith(setUp.messages.text(TextKey.PROBLEM_BELOW_MINIMUM_CURRENT));
    }
}
