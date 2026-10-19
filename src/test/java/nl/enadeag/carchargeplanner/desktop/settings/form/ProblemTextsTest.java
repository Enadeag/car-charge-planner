package nl.enadeag.carchargeplanner.desktop.settings.form;

import static org.assertj.core.api.Assertions.assertThat;

import nl.enadeag.carchargeplanner.desktop.text.Language;
import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.engine.setting.InvalidSetting.Rule;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class ProblemTextsTest {

    private static final Messages ENGLISH = new Messages(Language.ENGLISH);

    @Test
    void eachBrokenRuleHasItsOwnSentence() {
        assertThat(ProblemTexts.of(Rule.OUT_OF_RANGE, ENGLISH)).isEqualTo("This value is outside what is allowed here.");
        assertThat(ProblemTexts.of(Rule.NOT_BELOW_TARGET, ENGLISH)).isEqualTo("Must be below the target charge level.");
        assertThat(ProblemTexts.of(Rule.BELOW_MINIMUM_CURRENT, ENGLISH))
                .isEqualTo("Must not be below the minimum charge current.");
        assertThat(ProblemTexts.of(Rule.SAME_AS_PRIMARY, ENGLISH))
                .isEqualTo("Choose a different provider from the first one.");
        assertThat(ProblemTexts.of(Rule.NOT_AFTER_START, ENGLISH))
                .isEqualTo("Must end after it starts, on the same day.");
        assertThat(Arrays.stream(Rule.values()).map(rule -> ProblemTexts.of(rule, ENGLISH)).distinct())
                .hasSize(Rule.values().length);
    }

    @Test
    void theSentenceIsInTheOwnersLanguage() {
        assertThat(ProblemTexts.of(Rule.NOT_BELOW_TARGET, new Messages(Language.DUTCH)))
                .isEqualTo("Moet lager zijn dan het doelniveau.");
    }
}
