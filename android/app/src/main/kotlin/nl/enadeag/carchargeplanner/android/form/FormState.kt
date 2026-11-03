package nl.enadeag.carchargeplanner.android.form

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What a form's fields hold: the text of each, which ones the owner edited, which hold a typical value, and the
 * problems they show. The texts, the edits and the typical marks are kept in a [SavedStateHandle], so they survive
 * turning the phone and the system ending the app; the problems only live as long as the form, as Next or Save shows
 * them again. Only plain texts and lists of names go into the handle, never an API key.
 *
 * @param F the form's fields
 * @param handle where the texts and marks are kept, the screen's own
 * @param prefix sets this form's keys apart from the other forms' in the same handle
 * @param fields every field of the form
 */
class FormState<F : Enum<F>>(private val handle: SavedStateHandle, private val prefix: String,
    private val fields: List<F>) {

    private val problemsNow = MutableStateFlow<Map<F, FieldProblem>>(emptyMap())

    /** The problems the fields show now, by field; each until its field is edited. */
    val problems: StateFlow<Map<F, FieldProblem>> = problemsNow.asStateFlow()

    /**
     * Whether the form's texts were filled before, as when it comes back after the system ended the app.
     *
     * @return true once [fill] or [edit] put a text in
     */
    fun isFilled(): Boolean = handle.contains(key("filled"))

    /**
     * A field's text.
     *
     * @param field the field
     * @return what it holds; empty before it was filled
     */
    fun text(field: F): String = handle.get<String>(textKey(field)) ?: ""

    /**
     * Puts a value the app filled in, such as a stored one: not an edit of the owner's, so the field still stands
     * for the stored value exactly.
     *
     * @param field the field
     * @param text the value as the field shows it
     */
    fun fill(field: F, text: String) {
        handle[textKey(field)] = text
        handle[key("filled")] = true
        setNames("edited", edited() - field.name)
    }

    /**
     * Puts what the owner typed or chose: the field no longer stands for the stored value, its problem goes and so
     * does its typical mark.
     *
     * @param field the field
     * @param text what it holds now
     */
    fun edit(field: F, text: String) {
        handle[textKey(field)] = text
        handle[key("filled")] = true
        setNames("edited", edited() + field.name)
        setNames("typical", names("typical") - field.name)
        if (field in problemsNow.value) {
            problemsNow.value = problemsNow.value - field
        }
    }

    /**
     * Puts a value the app filled in for the owner, such as a typical value they asked for: it no longer stands for
     * the stored value, but it is not the owner's own either, so the field's typical mark is left to the caller.
     *
     * @param field the field
     * @param text the value as the field shows it
     */
    fun replace(field: F, text: String) {
        handle[textKey(field)] = text
        handle[key("filled")] = true
        setNames("edited", edited() + field.name)
        if (field in problemsNow.value) {
            problemsNow.value = problemsNow.value - field
        }
    }

    /**
     * Whether the owner changed the field since it was filled.
     *
     * @param field the field
     * @return true after [edit] or [replace], until the next [fill]
     */
    fun isEdited(field: F): Boolean = field.name in edited()

    /**
     * Whether any field was changed since the form was filled, as leaving would throw that away.
     *
     * @return true when a field was edited
     */
    fun isEdited(): Boolean = edited().isNotEmpty()

    /**
     * The fields that hold a typical value.
     *
     * @return the marked fields
     */
    fun typical(): Set<F> = fields.filter { it.name in names("typical") }.toSet()

    /**
     * Marks the fields that hold a typical value, replacing the marks.
     *
     * @param marked the fields to mark
     */
    fun markTypical(marked: Set<F>) {
        setNames("typical", marked.map { it.name }.toSet())
    }

    /**
     * Shows the problems found by Next or Save, replacing the ones shown before.
     *
     * @param found per field its problem; empty to show none
     */
    fun show(found: Map<F, FieldProblem>) {
        problemsNow.value = found.toMap()
    }

    /** Forgets everything of this form in the handle, for a row the owner removed. */
    fun forget() {
        fields.forEach { handle.remove<String>(textKey(it)) }
        listOf("filled", "edited", "typical").forEach { handle.remove<Any>(key(it)) }
        problemsNow.value = emptyMap()
    }

    private fun edited(): Set<String> = names("edited")

    private fun names(name: String): Set<String> = handle.get<ArrayList<String>>(key(name))?.toSet().orEmpty()

    private fun setNames(name: String, names: Set<String>) {
        handle[key(name)] = ArrayList(names.sorted())
    }

    private fun key(name: String) = "$prefix.$name"

    private fun textKey(field: F) = "$prefix.text.${field.name}"
}

/**
 * Forgets every key under these prefixes, as a screen that ends takes its texts, marks and place with it; the forms of
 * set-up and of Settings share their prefixes, so what one left behind would show in the other.
 *
 * @param prefixes the first part of each key to forget, such as "car" for "car.text.BATTERY"; a key equal to one is
 *     forgotten too
 */
fun SavedStateHandle.forget(prefixes: Collection<String>) {
    keys().filter { key -> prefixes.any { key == it || key.startsWith("$it.") } }.forEach { remove<Any>(it) }
}
