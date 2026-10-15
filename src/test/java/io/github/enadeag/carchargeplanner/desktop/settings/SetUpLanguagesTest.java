package io.github.enadeag.carchargeplanner.desktop.settings;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.enadeag.carchargeplanner.desktop.Language;
import io.github.enadeag.carchargeplanner.desktop.Scenes;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * In every language set-up shows each text whole, at the size it opens with: on every step, with the forecast
 * answered and with it failing, with a refused value and with the question before leaving.
 */
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

    @ParameterizedTest
    @EnumSource(Language.class)
    void noTextIsCutOffWhenTheForecastFails(Language language) throws Exception {
        setUp = new SetUpFixture(folder, language);
        setUp.provider.failing = true;
        var view = setUp.open();

        assertThat(SetUpScreens.problems(setUp, view, Scenes::cutOff)).isEmpty();
    }
}
