package nl.enadeag.carchargeplanner.desktop.settings.form;

import static org.assertj.core.api.Assertions.assertThat;

import nl.enadeag.carchargeplanner.desktop.settings.form.NumberText.Read;
import nl.enadeag.carchargeplanner.desktop.text.Language;
import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.desktop.text.TextKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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
    void aValueIsWrittenWithoutAThousandsSeparatorSoItReadsBackTheSame() {
        assertThat(english.write(1000, 0)).isEqualTo("1000");
        assertThat(dutch.write(1440.5, 1)).isEqualTo("1440,5");
        assertThat(english.read(english.write(1000, 0), 5, 0, false)).isEqualTo(new Read.Number(1000));
    }

    @Test
    void anUntouchedTextGivesTheHeldValueBackExactlyNotAsRoundedForShowing() {
        assertThat(english.read("0.33", 1.0 / 3, 2, false)).isEqualTo(new Read.Number(1.0 / 3));
        assertThat(dutch.read("0,33", 1.0 / 3, 2, false)).isEqualTo(new Read.Number(1.0 / 3));
    }

    @Test
    void eitherDecimalMarkIsReadInEitherLanguage() {
        assertThat(english.read("2.5", 1, 1, false)).isEqualTo(new Read.Number(2.5));
        assertThat(english.read("2,5", 1, 1, false)).isEqualTo(new Read.Number(2.5));
        assertThat(dutch.read("2,5", 1, 1, false)).isEqualTo(new Read.Number(2.5));
        assertThat(dutch.read("0.5", 1, 1, false)).as("not 5").isEqualTo(new Read.Number(0.5));
        assertThat(english.read(" 12 ", 1, 0, false)).isEqualTo(new Read.Number(12));
        assertThat(english.read("-3", 1, 0, false)).isEqualTo(new Read.Number(-3));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "four", "1,000.5", "1.000,5", "1e3", "+5", "NaN", "∞", "5 kWh", "-", ".5", "5."})
    void aTextThatIsNotANumberSaysSoAndDoesNotKeepTheHeldValue(String text) {
        assertThat(english.read(text, 4.2, 1, false)).isEqualTo(new Read.Wrong(TextKey.PROBLEM_NOT_A_NUMBER));
    }

    @Test
    void aTypedNumberOutsideAnyRangeIsGivenAsTyped() {
        assertThat(english.read("250", 50, 0, false)).isEqualTo(new Read.Number(250));
        assertThat(english.read("99999999999999999999", 50, 0, false)).isEqualTo(new Read.Number(1e20));
    }

    @Test
    void aWholeNumberFieldRefusesDecimalsRatherThanRoundingThem() {
        assertThat(english.read("16", 6, 0, true)).isEqualTo(new Read.Number(16));
        assertThat(english.read("16.5", 6, 0, true)).isEqualTo(new Read.Wrong(TextKey.PROBLEM_NOT_A_WHOLE_NUMBER));
        assertThat(dutch.read("16,0", 6, 0, true)).isEqualTo(new Read.Wrong(TextKey.PROBLEM_NOT_A_WHOLE_NUMBER));
        assertThat(english.read("sixteen", 6, 0, true)).isEqualTo(new Read.Wrong(TextKey.PROBLEM_NOT_A_NUMBER));
        assertThat(english.read("99999999999", 6, 0, true)).as("beyond what a whole-number field holds")
                .isEqualTo(new Read.Wrong(TextKey.PROBLEM_OUT_OF_RANGE));
    }
}
