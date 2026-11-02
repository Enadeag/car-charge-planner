package nl.enadeag.carchargeplanner.android.form

import java.util.Locale
import nl.enadeag.carchargeplanner.engine.setting.InvalidSetting
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

class NumberTextTest {

    private val english = NumberText(Locale.ENGLISH)
    private val dutch = NumberText(Locale.forLanguageTag("nl"))

    @Test
    fun aValueIsWrittenWithTheLanguagesDecimalMarkAndNoThousandsSeparator() {
        assertThat(english.write(1234.5, 2)).isEqualTo("1234.50")
        assertThat(dutch.write(1234.5, 2)).isEqualTo("1234,50")
        assertThat(english.write(60.0, 0)).isEqualTo("60")
    }

    @Test
    fun aValueIsRoundedHalfUpToTheDecimalsShown() {
        assertThat(english.write(0.125, 2)).isEqualTo("0.13")
        assertThat(english.write(2.5, 0)).isEqualTo("3")
        assertThat(english.write(-0.001, 0)).isEqualTo("0")
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = [
        "12.5|12.5", "12,5|12.5", "-3|-3.0", "-0,25|-0.25", "007|7.0", "0|0.0",
    ])
    fun eitherMarkAndALeadingMinusAreReadInEitherLanguage(text: String, value: Double) {
        assertThat(english.read(text)).isEqualTo(value)
        assertThat(dutch.read(text)).isEqualTo(value)
    }

    @ParameterizedTest
    @ValueSource(strings = ["", " 12", "12 ", "1 000", "1,000.5", "1.000,5", "1e3", "+5", "5.", ".5", "--5", "abc",
        "١٢", "１２", "NaN", "Infinity"])
    fun anythingElseIsNoNumber(text: String) {
        assertThat(english.read(text)).isNull()
        assertThat(dutch.read(text)).isNull()
    }

    @Test
    fun aNumberTooLargeForADoubleIsNoNumber() {
        assertThat(english.read("1" + "0".repeat(400))).isNull()
    }

    @Test
    fun aWholeNumberIsRead() {
        assertThat(english.readWhole("16")).isEqualTo(NumberText.WholeNumber.Read(16))
        assertThat(english.readWhole("-2")).isEqualTo(NumberText.WholeNumber.Read(-2))
    }

    @ParameterizedTest
    @ValueSource(strings = ["16.5", "16,0", "6.0"])
    fun aNumberWithDecimalsIsNoWholeNumberAndIsNotRounded(text: String) {
        assertThat(english.readWhole(text)).isEqualTo(NumberText.WholeNumber.Wrong(FieldProblem.NotAWholeNumber))
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "sixteen", "1e2", "+6", " 6"])
    fun aTextThatIsNoNumberIsNoWholeNumberEither(text: String) {
        assertThat(english.readWhole(text)).isEqualTo(NumberText.WholeNumber.Wrong(FieldProblem.NotANumber))
    }

    @Test
    fun aWholeNumberBeyondWhatTheFieldHoldsIsOutOfRange() {
        assertThat(english.readWhole("99999999999")).isEqualTo(
            NumberText.WholeNumber.Wrong(FieldProblem.Refused(InvalidSetting.Rule.OUT_OF_RANGE)))
    }
}
