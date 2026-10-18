package io.github.enadeag.carchargeplanner.desktop.text;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LanguageChoiceTest {

    private final LanguageChoice language = new LanguageChoice(Language.DUTCH);
    private final List<Language> told = new ArrayList<>();

    {
        language.onChange(messages -> told.add(messages.language()));
    }

    @Test
    void itSpeaksTheSystemsLanguageUntilTheOwnersChoiceIsKnown() {
        assertThat(language.messages().language()).isEqualTo(Language.DUTCH);
    }

    @Test
    void choosingAnotherLanguageTellsTheListenersAndSpeaksIt() {
        language.choose(Optional.of(Language.ENGLISH));

        assertThat(told).containsExactly(Language.ENGLISH);
        assertThat(language.messages().text(TextKey.QUIT)).isEqualTo("Quit");
    }

    @Test
    void followingTheSystemAgainSwitchesBackToItsLanguageAtOnce() {
        language.choose(Optional.of(Language.ENGLISH));

        language.choose(Optional.empty());

        assertThat(told).containsExactly(Language.ENGLISH, Language.DUTCH);
        assertThat(language.messages().language()).isEqualTo(Language.DUTCH);
    }

    @Test
    void aChoiceThatComesDownToTheLanguageSpokenTellsNobody() {
        language.choose(Optional.of(Language.DUTCH));
        language.choose(Optional.empty());

        assertThat(told).isEmpty();
    }
}
