package io.github.enadeag.carchargeplanner.desktop.settings;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.enadeag.carchargeplanner.desktop.Language;
import io.github.enadeag.carchargeplanner.desktop.FxTest;
import io.github.enadeag.carchargeplanner.desktop.Scenes;
import io.github.enadeag.carchargeplanner.desktop.TextScale;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * In every language set-up shows each text whole, at the size it opens with and with the system's text at 150 and
 * 200 % (and then on its own, too): on every step, with the forecast
 * answered and with it failing, with a refused value and with the question before leaving.
 */
@FxTest
class SetUpLanguagesTest {

    @TempDir
    Path folder;

    private SetUpFixture setUp;

    @AfterEach
    void close() throws Exception {
        setUp.close();
    }

    @ParameterizedTest
    @EnumSource(Language.class)
    void noTextIsCutOff(Language language) throws Exception {
        setUp = new SetUpFixture(folder, language);
        var view = setUp.open();

        assertThat(SetUpScreens.problems(setUp, view, Scenes::cutOff)).isEmpty();
    }

    static Stream<Arguments> languagesAndScales() {
        return Stream.of(Language.values()).flatMap(language -> Stream.of(TextScale.values())
                .map(scale -> Arguments.of(language, scale)));
    }

    /** As the owner's system makes the text larger, at 150 and 200 %, in a window as much larger as the screen allows. */
    @ParameterizedTest
    @MethodSource("languagesAndScales")
    void withLargerTextNoTextIsCutOffOrOverlapsEither(Language language, TextScale scale) throws Exception {
        setUp = new SetUpFixture(folder, language);
        var view = setUp.open(scale);

        assertThat(SetUpScreens.problems(setUp, view, node -> {
            var found = new ArrayList<>(Scenes.cutOff(node));
            found.addAll(Scenes.overlapping(node));
            return found;
        })).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(Language.class)
    void noTextIsCutOffWhenTheForecastFails(Language language) throws Exception {
        setUp = new SetUpFixture(folder, language);
        setUp.provider.failing = true;
        var view = setUp.open();

        assertThat(SetUpScreens.problems(setUp, view, Scenes::cutOff)).isEmpty();
    }
}
