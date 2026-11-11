package nl.enadeag.carchargeplanner.android.form

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The rows of a list form, such as the panel arrays, each with its own fields, kept in a [SavedStateHandle] by a
 * number that stays with the row when others are added or removed.
 */
internal class Rows<F : Enum<F>>(private val handle: SavedStateHandle, private val prefix: String,
    private val fields: List<F>) {

    private val ids = MutableStateFlow(handle.get<ArrayList<Int>>("$prefix.rows")?.toList().orEmpty())
    private val states = HashMap<Int, FormState<F>>()

    /** The rows' numbers, in the order shown. */
    val rows: StateFlow<List<Int>> = ids.asStateFlow()

    /**
     * Whether the rows were made before, as when the form comes back after the system ended the app.
     *
     * @return true once a row was added, or the list was emptied by [clear]
     */
    fun isFilled(): Boolean = handle.contains("$prefix.rows")

    fun state(id: Int): FormState<F> = states.getOrPut(id) { FormState(handle, "$prefix.$id", fields) }

    fun add(): Int {
        val id = (ids.value.maxOrNull() ?: -1) + 1
        keep(ids.value + id)
        return id
    }

    fun remove(id: Int) {
        state(id).forget()
        states.remove(id)
        keep(ids.value - id)
    }

    fun clear() {
        ids.value.forEach { state(it).forget() }
        states.clear()
        keep(emptyList())
    }

    /**
     * A number the form keeps with a row, such as where in the stored list it came from.
     *
     * @return the number; null when none was kept
     */
    fun number(id: Int, name: String): Int? = handle.get<Int>("$prefix.$id.$name")

    /**
     * @param value the number; null to forget it
     */
    fun keepNumber(id: Int, name: String, value: Int?) {
        if (value == null) handle.remove<Int>("$prefix.$id.$name") else handle["$prefix.$id.$name"] = value
    }

    fun decimal(id: Int, name: String): Double? = handle.get<String>("$prefix.$id.$name")?.toDoubleOrNull()

    /**
     * @param value the number, kept exactly, as a text, which the handle keeps like the forms' own; null to forget it
     */
    fun keepDecimal(id: Int, name: String, value: Double?) {
        if (value == null) handle.remove<String>("$prefix.$id.$name") else handle["$prefix.$id.$name"] = value.toString()
    }

    private fun keep(rows: List<Int>) {
        handle["$prefix.rows"] = ArrayList(rows)
        ids.value = rows
    }
}
