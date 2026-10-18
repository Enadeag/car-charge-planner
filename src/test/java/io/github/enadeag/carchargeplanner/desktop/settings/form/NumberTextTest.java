package io.github.enadeag.carchargeplanner.desktop.settings.form;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.enadeag.carchargeplanner.desktop.text.Language;
import io.github.enadeag.carchargeplanner.desktop.text.Messages;
import org.junit.jupiter.api.Test;

class NumberTextTest {

    private final NumberText english = new NumberText(new Messages(Language.ENGLISH));
    private final NumberText dutch = new NumberText(new Messages(Language.DUTCH));

    @Test
    void aValueIsWrittenWithTheFieldsDecimalsTheWayTheLanguageWritesNumbers() {
        assertThat(english.write(0.25, 2)).isEqualTo("0.25");
        assertThat(dutch.write(0.25, 2)).isEqualTo("0,25");
        assertThat(english.write(7.25, 1)).isEqualTo("7.3");
        assertThat(english.write(16, 0)).isEqualTo("16");
    }

    @Test
    void anUntouchedTextGivesTheHeldValueBackExactlyNotAsRoundedForShowing() {
        assertThat(english.read("0.33", 1.0 / 3, 2)).isEqualTo(1.0 / 3);
        assertThat(dutch.read("0,33", 1.0 / 3, 2)).isEqualTo(1.0 / 3);
    }

    @Test
    void aTypedNumberIsReadTheWayTheLanguageWritesIt() {
        assertThat(english.read("2.5", 1, 1)).isEqualTo(2.5);
        assertThat(dutch.read("2,5", 1, 1)).isEqualTo(2.5);
        assertThat(english.read(" 12 ", 1, 0)).isEqualTo(12);
    }

    @Test
    void aTextThatIsNotANumberKeepsTheHeldValue() {
        assertThat(english.read("", 4.2, 1)).isEqualTo(4.2);
        assertThat(english.read("four", 4.2, 1)).isEqualTo(4.2);
        assertThat(dutch.read("2.5", 4.2, 1)).as("a point is not the Dutch decimal sign").isEqualTo(4.2);
    }

    @Test
    void aTypedNumberOutsideTheRangeIsGivenAsTyped() {
        assertThat(english.read("250", 50, 0)).isEqualTo(250);
    }
}
