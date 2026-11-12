package nl.enadeag.carchargeplanner.android.form

import java.time.Duration
import nl.enadeag.carchargeplanner.engine.config.Percent
import nl.enadeag.carchargeplanner.engine.setting.ConfigSetting
import nl.enadeag.carchargeplanner.engine.setting.InvalidSetting

/**
 * Reads a form's texts into values and collects what is wrong with them. A field the owner did not edit, or whose
 * text is still how the stored value is written, gives back the stored value exactly, not as rounded for showing.
 * A value it cannot read gives back the stored one, and its problem is noted, so the reading goes on and every field
 * gets its own problem.
 *
 * @param F the form's fields
 * @param state the form's texts
 * @param numbers how the screen's language writes numbers
 */
class FieldReader<F : Enum<F>>(private val state: FormState<F>, private val numbers: NumberText) {

    /** The problems found so far, by field, in the order found. */
    val problems = LinkedHashMap<F, FieldProblem>()

    /**
     * A number field's value.
     *
     * @param field the field
     * @param stored the stored value, in the engine's unit
     * @param decimals the decimals the field shows
     * @param scale how many of the field's unit make one of the engine's, such as 100 for a share shown in %
     * @param untouchedIsStored false when [stored] is not what the field was filled with, so its text counts
     * @return the value in the engine's unit
     */
    fun number(field: F, stored: Double, decimals: Int, scale: Double = 1.0, untouchedIsStored: Boolean = true):
        Double {
        val text = state.text(field)
        if (untouchedIsStored && (!state.isEdited(field) || text == numbers.write(stored * scale, decimals))) {
            return stored
        }
        val typed = numbers.read(text)
        if (typed == null) {
            problems[field] = FieldProblem.NotANumber
            return stored
        }
        return typed / scale
    }

    /**
     * A percentage field's value, from 0 to 100 as [Percent] has it.
     *
     * @param field the field
     * @param stored the stored percentage
     * @param decimals the decimals the field shows
     * @return the percentage; the stored one when the text is refused
     */
    fun percent(field: F, stored: Percent, decimals: Int): Percent {
        val value = number(field, stored.value(), decimals)
        if (!(value >= 0 && value <= 100)) {
            problems.putIfAbsent(field, FieldProblem.Refused(InvalidSetting.Rule.OUT_OF_RANGE))
            return stored
        }
        return Percent(value)
    }

    /**
     * A whole-number field's value, such as amps; a number with decimals is refused, never rounded.
     *
     * @param field the field
     * @param stored the stored value
     * @return the value; the stored one when the text is refused
     */
    fun whole(field: F, stored: Int): Int {
        val text = state.text(field)
        if (!state.isEdited(field) || text == stored.toString()) {
            return stored
        }
        return when (val read = numbers.readWhole(text)) {
            is NumberText.WholeNumber.Read -> read.value
            is NumberText.WholeNumber.Wrong -> stored.also { problems[field] = read.problem }
        }
    }

    /**
     * A duration field's value, in whole minutes.
     *
     * @param field the field
     * @param stored the stored duration, in whole minutes
     * @return the duration; the stored one when the text is refused
     */
    fun minutes(field: F, stored: Duration): Duration {
        val whole = whole(field, Math.toIntExact(stored.toMinutes()))
        return if (whole.toLong() == stored.toMinutes()) stored else Duration.ofMinutes(whole.toLong())
    }

    /**
     * Has the engine's records check the values read, once every field could be read.
     *
     * @param T what the records make
     * @param fieldOf the field that shows a problem of a setting
     * @param make makes the records; an [InvalidSetting] it throws becomes its field's problem
     * @return what the records made; null when a field could not be read or a record refused a value
     */
    fun <T> records(fieldOf: (ConfigSetting) -> F, make: () -> T): T? {
        if (problems.isNotEmpty()) {
            return null
        }
        return try {
            make()
        } catch (refused: InvalidSetting) {
            problems[fieldOf(refused.setting())] = FieldProblem.Refused(refused.rule())
            null
        }
    }
}
