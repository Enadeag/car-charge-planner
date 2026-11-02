package nl.enadeag.carchargeplanner.android.form

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.flow.StateFlow
import nl.enadeag.carchargeplanner.engine.config.EngineConfig
import nl.enadeag.carchargeplanner.engine.config.PanelArray
import nl.enadeag.carchargeplanner.engine.config.TypicalValues
import nl.enadeag.carchargeplanner.engine.setting.ConfigSetting

/**
 * The panel arrays, at least one, each with a name, its size in kWp, its tilt and direction in degrees and its
 * losses in %; each value but the name may be typical.
 *
 * @param handle where the rows and their texts are kept
 * @param numbers how the screen's language writes numbers
 */
class PanelsForm(handle: SavedStateHandle, private val numbers: NumberText) : ConfigForm {

    /**
     * A field of an array's row.
     *
     * @property setting the engine's name for it; null for the name, which is never refused or typical
     * @property decimals the decimals it shows
     */
    enum class Field(val setting: ConfigSetting?, val decimals: Int) {
        /** The owner's name for the array, such as "east roof"; may be empty. */
        NAME(null, 0),
        /** The peak power, in kWp. */
        SIZE(ConfigSetting.PANEL_SIZE, 2),
        /** The tilt from flat, in degrees. */
        TILT(ConfigSetting.PANEL_TILT, 0),
        /** The direction, in degrees from south: -90 east, 90 west. */
        DIRECTION(ConfigSetting.PANEL_DIRECTION, 0),
        /** The share of the power lost, in %, which the engine keeps as a share of 1. */
        LOSSES(ConfigSetting.PANEL_LOSSES, 0),
    }

    private companion object {
        /** Where in the stored list a row came from, so an untouched value is the stored one exactly. */
        const val STORED_AT = "storedAt"
    }

    private val rows = Rows(handle, "panels", Field.entries)

    /** The arrays' rows, by number, in the order shown. */
    val rowIds: StateFlow<List<Int>> = rows.rows

    /**
     * A row's text.
     *
     * @param row the row's number
     * @param field the field
     * @return what it holds
     */
    fun text(row: Int, field: Field): String = rows.state(row).text(field)

    /**
     * Puts what the owner typed in a row: the field's problem and typical mark go.
     *
     * @param row the row's number
     * @param field the field
     * @param text what it holds now
     */
    fun edit(row: Int, field: Field, text: String) = rows.state(row).edit(field, text)

    /**
     * The problems a row's fields show now.
     *
     * @param row the row's number
     * @return its problems, by field
     */
    fun problems(row: Int): StateFlow<Map<Field, FieldProblem>> = rows.state(row).problems

    /**
     * The fields of a row that hold a typical value.
     *
     * @param row the row's number
     * @return the marked fields
     */
    fun typical(row: Int): Set<Field> = rows.state(row).typical()

    /**
     * Adds an array with the typical values, marked as such.
     *
     * @return its row's number
     */
    fun add(): Int {
        val id = rows.add()
        show(rows.state(id), PanelArray.TYPICAL, FormState<Field>::replace)
        rows.state(id).markTypical(marked(TypicalValues.PANEL_SETTINGS))
        return id
    }

    /**
     * Removes an array; the last one stays, as a system has at least one.
     *
     * @param row the row's number
     * @return false when it is the last one, which is kept
     */
    fun remove(row: Int): Boolean {
        if (rows.rows.value.size <= 1) {
            return false
        }
        rows.remove(row)
        return true
    }

    /**
     * Fills a row with the typical values and marks them, as the owner asked; the name stays.
     *
     * @param row the row's number
     */
    fun useTypical(row: Int) {
        val state = rows.state(row)
        show(state, PanelArray.TYPICAL, FormState<Field>::replace, withName = false)
        state.markTypical(marked(TypicalValues.PANEL_SETTINGS))
    }

    override fun load(config: EngineConfig) {
        rows.clear()
        config.system().panels().forEachIndexed { index, array ->
            val id = rows.add()
            val state = rows.state(id)
            show(state, array, FormState<Field>::fill)
            state.markTypical(marked(config.typical().ofPanel(index)))
            rows.keepNumber(id, STORED_AT, index)
        }
    }

    override fun read(config: EngineConfig): EngineConfig? = evaluate(config).first

    override fun check(config: EngineConfig): EngineConfig? {
        val (result, problems) = evaluate(config)
        rows.rows.value.forEach { id -> rows.state(id).show(problems[id].orEmpty()) }
        return result
    }

    override fun isEdited(): Boolean {
        val ids = rows.rows.value
        return ids.any { rows.state(it).isEdited() } || ids.map { rows.number(it, STORED_AT) } != ids.indices.toList()
    }

    private fun evaluate(config: EngineConfig): Pair<EngineConfig?, Map<Int, Map<Field, FieldProblem>>> {
        val stored = config.system().panels()
        val arrays = mutableListOf<PanelArray>()
        val typical = mutableListOf<Set<ConfigSetting>>()
        val problems = linkedMapOf<Int, Map<Field, FieldProblem>>()
        for (id in rows.rows.value) {
            val state = rows.state(id)
            val reader = FieldReader(state, numbers)
            val from = rows.number(id, STORED_AT)?.let { stored.getOrNull(it) }
            val base = from ?: PanelArray.TYPICAL
            val known = from != null
            val size = reader.number(Field.SIZE, base.kwp(), Field.SIZE.decimals, untouchedIsStored = known)
            val tilt = reader.number(Field.TILT, base.tiltDeg(), Field.TILT.decimals, untouchedIsStored = known)
            val direction = reader.number(Field.DIRECTION, base.azimuthDeg(), Field.DIRECTION.decimals,
                untouchedIsStored = known)
            val losses = reader.number(Field.LOSSES, base.systemLosses(), Field.LOSSES.decimals, scale = 100.0,
                untouchedIsStored = known)
            val array = reader.records({ setting -> Field.entries.first { it.setting == setting } }) {
                PanelArray(state.text(Field.NAME), size, tilt, direction, losses)
            }
            if (array == null) {
                problems[id] = reader.problems.toMap()
            } else {
                arrays += array
                typical += state.typical().mapNotNull { it.setting }.toSet()
            }
        }
        if (problems.isNotEmpty()) {
            return null to problems
        }
        // an array past the end of the list has none, as the file writes it: no change where nothing changed
        val marks = TypicalValues(config.typical().system(), typical.dropLastWhile { it.isEmpty() })
        return config.withSystem(config.system().withPanels(arrays), marks) to emptyMap()
    }

    private fun marked(typical: Set<ConfigSetting>): Set<Field> =
        Field.entries.filter { field -> field.setting?.let { it in typical } == true }.toSet()

    private fun show(state: FormState<Field>, array: PanelArray, put: FormState<Field>.(Field, String) -> Unit,
        withName: Boolean = true) {
        if (withName) {
            state.put(Field.NAME, array.name())
        }
        state.put(Field.SIZE, numbers.write(array.kwp(), Field.SIZE.decimals))
        state.put(Field.TILT, numbers.write(array.tiltDeg(), Field.TILT.decimals))
        state.put(Field.DIRECTION, numbers.write(array.azimuthDeg(), Field.DIRECTION.decimals))
        state.put(Field.LOSSES, numbers.write(array.systemLosses() * 100, Field.LOSSES.decimals))
    }
}
