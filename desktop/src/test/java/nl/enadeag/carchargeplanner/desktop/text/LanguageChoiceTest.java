package nl.enadeag.carchargeplanner.desktop.text;

import static org.assertj.core.api.Assertions.assertThat;

import nl.enadeag.carchargeplanner.desktop.system.ClockFormat;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
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
        language.choose(Language.ENGLISH);

        assertThat(told).containsExactly(Language.ENGLISH);
        assertThat(language.messages().text(TextKey.QUIT)).isEqualTo("Quit");
    }

    @Test
    void followingTheSystemAgainSwitchesBackToItsLanguageAtOnce() {
        language.choose(Language.ENGLISH);

        language.followSystem();

        assertThat(told).containsExactly(Language.ENGLISH, Language.DUTCH);
        assertThat(language.messages().language()).isEqualTo(Language.DUTCH);
    }

    @Test
    void theTextsOfALanguageBeingChosenLeaveTheLanguageSpokenAlone() {
        assertThat(language.of(Language.ENGLISH).text(TextKey.QUIT)).isEqualTo("Quit");
        assertThat(language.ofSystem()).isSameAs(language.messages());

        assertThat(language.messages().language()).isEqualTo(Language.DUTCH);
        assertThat(told).isEmpty();
    }

    @Test
    void aChoiceThatComesDownToTheLanguageSpokenTellsNobody() {
        language.choose(Language.DUTCH);
        language.followSystem();

        assertThat(told).isEmpty();
    }

    @Test
    void aChangeOfTheSystemsClockTellsTheListenersAndWritesTheTimesOnIt() {
        var clocks = new ArrayList<ClockFormat>();
        language.onChange(messages -> clocks.add(messages.clock()));

        language.useClock(ClockFormat.TWELVE_HOUR);

        assertThat(clocks).containsExactly(ClockFormat.TWELVE_HOUR);
        assertThat(told).containsExactly(Language.DUTCH);
        assertThat(language.messages().time(LocalTime.of(19, 30))).isEqualTo("7:30 p.m.");
    }

    @Test
    void theClockStaysWhenTheLanguageChangesAndForALanguageBeingChosen() {
        language.useClock(ClockFormat.TWELVE_HOUR);

        language.choose(Language.ENGLISH);

        assertThat(language.messages().clock()).isEqualTo(ClockFormat.TWELVE_HOUR);
        assertThat(language.of(Language.DUTCH).clock()).isEqualTo(ClockFormat.TWELVE_HOUR);
    }

    @Test
    void theClockWrittenAlreadyTellsNobody() {
        language.useClock(ClockFormat.LANGUAGE);

        assertThat(told).isEmpty();
    }
}
