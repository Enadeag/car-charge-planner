package nl.enadeag.carchargeplanner.android.form

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormatSymbols
import java.util.Locale
import nl.enadeag.carchargeplanner.engine.setting.InvalidSetting

/**
 * How a number field writes a value in the app's language and reads what the owner typed. It writes the language's
 * decimal mark, a comma or a point, with ASCII digits and no thousands separator; it reads either mark, a leading
 * minus and ASCII digits, and nothing else: no thousands separator, exponent, space or plus sign, and an empty field
 * is no number. Whether the number fits is left to the engine's rules.
 *
 * @param locale the language the screen speaks, from its configuration
 */
class NumberText(locale: Locale) {

    private companion object {
        val NUMBER = Regex("-?[0-9]+([.,][0-9]+)?")
        val WHOLE = Regex("-?[0-9]+")
    }

    private val mark = if (DecimalFormatSymbols.getInstance(locale).decimalSeparator == ',') ',' else '.'

    /**
     * A value as the field shows it.
     *
     * @param value the value, finite
     * @param decimals how many decimals the field shows, 0 or more; the value is rounded half up to them
     * @return such as "0.25" in English and "0,25" in Dutch for two decimals
     */
    fun write(value: Double, decimals: Int): String {
        require(decimals >= 0) { "decimals must not be negative, not $decimals" }
        val text = BigDecimal.valueOf(value).setScale(decimals, RoundingMode.HALF_UP).toPlainString()
        return if (mark == '.') text else text.replace('.', mark)
    }

    /**
     * The number a text stands for.
     *
     * @param text what the field holds
     * @return the number; null when the text is no number by the rules above, or too large to be one
     */
    fun read(text: String): Double? =
        if (NUMBER.matches(text)) text.replace(',', '.').toDouble().takeIf { it.isFinite() } else null

    /**
     * The whole number a text stands for.
     *
     * @param text what the field holds
     * @return the number, the problem with it: [FieldProblem.NotAWholeNumber] for a number with decimals, even ",0",
     *     [FieldProblem.NotANumber] for no number, and the engine's range for one beyond what the field can hold
     */
    fun readWhole(text: String): WholeNumber = when {
        WHOLE.matches(text) -> text.toIntOrNull()?.let { WholeNumber.Read(it) }
            ?: WholeNumber.Wrong(FieldProblem.Refused(InvalidSetting.Rule.OUT_OF_RANGE))
        NUMBER.matches(text) -> WholeNumber.Wrong(FieldProblem.NotAWholeNumber)
        else -> WholeNumber.Wrong(FieldProblem.NotANumber)
    }

    /** A whole number as read from a field, or what is wrong with the text. */
    sealed interface WholeNumber {

        /**
         * The text is a whole number.
         *
         * @property value the number
         */
        data class Read(val value: Int) : WholeNumber

        /**
         * The text is not a whole number the field can hold.
         *
         * @property problem what the field says
         */
        data class Wrong(val problem: FieldProblem) : WholeNumber
    }
}
