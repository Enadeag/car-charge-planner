package nl.enadeag.carchargeplanner.android.form

import kotlinx.coroutines.flow.StateFlow
import nl.enadeag.carchargeplanner.engine.config.EngineConfig

/**
 * A [ConfigForm] of fields that the owner types into, on a [FormState].
 *
 * @param F the form's fields
 * @param state the form's texts, edits and typical marks
 * @param numbers how the screen's language writes numbers
 */
abstract class FieldsForm<F : Enum<F>>(protected val state: FormState<F>, protected val numbers: NumberText) :
    ConfigForm {

    /** The problems the fields show now, by field. */
    val problems: StateFlow<Map<F, FieldProblem>> get() = state.problems

    /**
     * A field's text.
     *
     * @param field the field
     * @return what it holds
     */
    fun text(field: F): String = state.text(field)

    /**
     * Puts what the owner typed: the field's problem and typical mark go.
     *
     * @param field the field
     * @param text what it holds now
     */
    open fun edit(field: F, text: String) = state.edit(field, text)

    /**
     * The fields that hold a typical value.
     *
     * @return the marked fields
     */
    fun typical(): Set<F> = state.typical()

    override fun read(config: EngineConfig): EngineConfig? = evaluate(config).first

    override fun check(config: EngineConfig): EngineConfig? {
        val (result, problems) = evaluate(config)
        state.show(problems)
        return result
    }

    override fun isEdited() = state.isEdited()

    private fun evaluate(config: EngineConfig): Pair<EngineConfig?, Map<F, FieldProblem>> {
        val reader = FieldReader(state, numbers)
        val result = readInto(config, reader)
        return if (reader.problems.isEmpty()) result to emptyMap() else null to reader.problems.toMap()
    }

    /**
     * Lays the fields over a config.
     *
     * @param config the config to change
     * @param reader reads the fields and notes their problems
     * @return the changed config; null when a value is refused, whose problem the reader noted
     */
    protected abstract fun readInto(config: EngineConfig, reader: FieldReader<F>): EngineConfig?
}
