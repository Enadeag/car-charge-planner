package nl.enadeag.carchargeplanner.desktop.settings.setup;

import static org.assertj.core.api.Assertions.assertThat;

import nl.enadeag.carchargeplanner.desktop.Contrast;
import nl.enadeag.carchargeplanner.desktop.CssWarnings;
import nl.enadeag.carchargeplanner.desktop.FxTest;
import nl.enadeag.carchargeplanner.desktop.text.Language;
import java.nio.file.Path;
import javafx.application.ColorScheme;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * WCAG AA in set-up, light and dark, in both languages: every step with typical values tagged, the check and the
 * dashboard's preview answered, a refused value and the question before leaving.
 */
@FxTest
class SetUpContrastTest {

    @TempDir
    Path folder;

    private SetUpFixture setUp;

    @AfterEach
    void close() throws Exception {
        setUp.close();
    }

    @ParameterizedTest
    @EnumSource(ColorScheme.class)
    void everyTextStandsOutEnoughInEnglish(ColorScheme scheme) throws Exception {
        check(Language.ENGLISH, scheme);
    }

    @ParameterizedTest
    @EnumSource(ColorScheme.class)
    void everyTextStandsOutEnoughInDutch(ColorScheme scheme) throws Exception {
        check(Language.DUTCH, scheme);
    }

    private void check(Language language, ColorScheme scheme) throws Exception {
        setUp = new SetUpFixture(folder, language);
        try (var css = new CssWarnings()) {
            var view = setUp.open(scheme);

            assertThat(SetUpScreens.problems(setUp, view, Contrast::problemsIn)).isEmpty();
            assertThat(css.warnings()).isEmpty();
        }
    }

    @ParameterizedTest
    @EnumSource(ColorScheme.class)
    void whileReadingOrSavingAndWhenReadingFailedEveryTextStandsOutEnough(ColorScheme scheme) throws Exception {
        setUp = new SetUpFixture(folder, Language.DUTCH);

        assertThat(SetUpBusyScreens.setUp(folder, Language.DUTCH, scheme, Contrast::problemsIn)).isEmpty();
    }
}
