package nl.enadeag.carchargeplanner.android.form

import androidx.lifecycle.SavedStateHandle
import java.time.Year
import java.util.Optional
import java.util.OptionalInt
import kotlinx.coroutines.flow.StateFlow
import nl.enadeag.carchargeplanner.engine.config.CompassPoint
import nl.enadeag.carchargeplanner.engine.config.EngineConfig
import nl.enadeag.carchargeplanner.engine.config.PanelArray
import nl.enadeag.carchargeplanner.engine.config.PanelSize
import nl.enadeag.carchargeplanner.engine.config.TiltChoice
import nl.enadeag.carchargeplanner.engine.config.TypicalValues
import nl.enadeag.carchargeplanner.engine.setting.ConfigSetting
import nl.enadeag.carchargeplanner.engine.setting.InvalidSetting

/**
 * The panel arrays, at least one, each with its number of panels, direction, tilt, year placed, power per panel,
 * losses in % and a name. A new array starts empty but for the typical losses. An array stored by its peak power keeps
 * it while its count, year and power stay empty; a stored tilt or direction shows as the nearest choice and stays as
 * stored until the owner chooses.
 *
 * @param handle where the rows and their texts are kept
 * @param numbers how the screen's language writes numbers
 * @param thisYear the current year, which a year placed the owner typed may not be after
 * @param newWhenTypical true in set-up, which shows a loaded array whose size is a typical value as a new, empty one
 */
class PanelsForm(handle: SavedStateHandle, private val numbers: NumberText, private val thisYear: () -> Year,
    private val newWhenTypical: Boolean = false) : ConfigForm {

    /**
     * A field of an array's row, in the order shown.
     *
     * @property setting the engine's name for it; null for the name, which is never refused or typical
     */
    enum class Field(val setting: ConfigSetting?) {
        /** The number of panels, whole; empty for an array kept by its peak power and for a new one. */
        COUNT(ConfigSetting.PANEL_COUNT),
        /** The direction, a [CompassPoint]'s name; empty before a choice on a new array. */
        DIRECTION(ConfigSetting.PANEL_DIRECTION),
        /** The tilt, a [TiltChoice]'s name; empty before a choice on a new array. */
        TILT(ConfigSetting.PANEL_TILT),
        /** The year the panels were placed, whole; may stay empty when the power is typed. */
        YEAR(ConfigSetting.PANEL_YEAR),
        /** The power per panel in Wp, whole; empty for the typical one of the year. */
        POWER(ConfigSetting.PANEL_POWER),
        /** The share of the power lost, in %, which the engine keeps as a share of 1. */
        LOSSES(ConfigSetting.PANEL_LOSSES),
        /** The owner's name for the array, such as "east roof"; may be empty. */
        NAME(null),
    }

    private companion object {
        /** Where in the stored list a row came from, so an untouched value is the stored one exactly. */
        const val STORED_AT = "storedAt"

        const val KEPT_KWP = "keptKwp"

        /** Kept as 1 for a row set-up shows as new, which has no stored values of its own. */
        const val AS_NEW = "asNew"

        const val LOSSES_DECIMALS = 0

        val SIZE_FIELDS = listOf(Field.COUNT, Field.YEAR, Field.POWER)
    }

    private class Entered(val count: Int, val year: Int?, val power: Int?) {
        fun size(): PanelSize.Panels = PanelSize.Panels(count, power?.let { OptionalInt.of(it) } ?: OptionalInt.empty(),
            Optional.ofNullable(year?.let { Year.of(it) }))
    }

    private val rows = Rows(handle, "panels", Field.entries)

    /** The arrays' rows, by number, in the order shown. */
    val rowIds: StateFlow<List<Int>> = rows.rows

    /**
     * A row's text.
     *
     * @param row the row's number
     * @param field the field
     * @return what it holds; for the direction and the tilt, the choice's name or empty
     */
    fun text(row: Int, field: Field): String = rows.state(row).text(field)

    /**
     * Puts what the owner typed in a row: the field's problem and typical mark go.
     *
     * @param row the row's number
     * @param field a field the owner types in, not the direction or the tilt
     * @param text what it holds now
     */
    fun edit(row: Int, field: Field, text: String) = rows.state(row).edit(field, text)

    /**
     * The direction a row shows: the owner's choice, or the one nearest to the stored degrees.
     *
     * @param row the row's number
     * @return the point; null before a choice on a new array, and for a restored name this version does not know
     */
    fun direction(row: Int): CompassPoint? = point(text(row, Field.DIRECTION))

    /**
     * The tilt a row shows: the owner's choice, or the one nearest to the stored degrees.
     *
     * @param row the row's number
     * @return the choice; null before a choice on a new array, and for a restored name this version does not know
     */
    fun tilt(row: Int): TiltChoice? = tiltChoice(text(row, Field.TILT))

    /**
     * Chooses a row's direction, which from now on replaces the stored degrees.
     *
     * @param row the row's number
     * @param point the owner's choice
     */
    fun choose(row: Int, point: CompassPoint) = rows.state(row).edit(Field.DIRECTION, point.name)

    /**
     * Chooses a row's tilt, which from now on replaces the stored degrees.
     *
     * @param row the row's number
     * @param tilt the owner's choice
     */
    fun choose(row: Int, tilt: TiltChoice) = rows.state(row).edit(Field.TILT, tilt.name)

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
     * The peak power a row's stored array keeps while its count, year and power stay empty.
     *
     * @param row the row's number
     * @return the stored kWp; null for an array of panels, a new one, or once one of those fields holds text
     */
    fun keptKwp(row: Int): Double? = kept(row)?.kwp()

    /**
     * The peak power a row's typed panels give together, which follows the fields at once.
     *
     * @param row the row's number
     * @return the kWp; null while the count, the year or the power is missing or refused
     */
    fun together(row: Int): Double? {
        val state = rows.state(row)
        val entered = entered(state, LinkedHashMap()) ?: return null
        return try {
            size(state, entered).kwp()
        } catch (ignored: InvalidSetting) {
            null
        }
    }

    /**
     * The power per panel the app counts on for a row while its power is empty: the typical one for the year typed.
     *
     * @param row the row's number
     * @return the Wp; null while the year is not one the array may have
     */
    fun typicalWattPeak(row: Int): Int? {
        val year = (numbers.readWhole(text(row, Field.YEAR).trim()) as? NumberText.WholeNumber.Read)?.value
            ?: return null
        return PanelSize.Panels.typicalWattPeakFor(year, thisYear()).takeIf { it.isPresent }?.asInt
    }

    /**
     * Adds an array, empty but for the typical losses, marked as such.
     *
     * @return its row's number
     */
    fun add(): Int {
        val id = rows.add()
        rows.keepNumber(id, STORED_AT, null)
        rows.keepNumber(id, AS_NEW, null)
        rows.keepDecimal(id, KEPT_KWP, null)
        showNew(rows.state(id), FormState<Field>::replace)
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

    override fun load(config: EngineConfig) {
        rows.clear()
        config.system().panels().forEachIndexed { index, array ->
            val id = rows.add()
            val state = rows.state(id)
            val typical = config.typical().ofPanel(index)
            val asNew = newWhenTypical && ConfigSetting.PANEL_SIZE in typical
            if (asNew) {
                showNew(state, FormState<Field>::fill)
            } else {
                show(state, array)
                state.markTypical(marked(typical))
            }
            rows.keepNumber(id, STORED_AT, index)
            rows.keepNumber(id, AS_NEW, if (asNew) 1 else null)
            rows.keepDecimal(id, KEPT_KWP, (array.size() as? PanelSize.Peak)?.kwp()?.takeUnless { asNew })
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
            val index = rows.number(id, STORED_AT)
            val from = index?.takeIf { rows.number(id, AS_NEW) == null }?.let { stored.getOrNull(it) }
            val array = readArray(id, state, from, reader)
            if (array == null) {
                problems[id] = reader.problems.toMap()
            } else {
                arrays += array
                // a kept peak power stays as typical as it was stored
                val keptTypical = if (array.size() is PanelSize.Peak && from != null) {
                    config.typical().ofPanel(index!!).filter { it == ConfigSetting.PANEL_SIZE }
                } else emptyList()
                typical += state.typical().mapNotNull { it.setting }.toSet() + keptTypical
            }
        }
        if (problems.isNotEmpty()) {
            return null to problems
        }
        // an array past the end of the list has none, as the file writes it: no change where nothing changed
        val marks = TypicalValues(config.typical().system(), typical.dropLastWhile { it.isEmpty() })
        return config.withSystem(config.system().withPanels(arrays), marks) to emptyMap()
    }

    /** Every field's problem is noted, in the order shown, before the engine's records check the values. */
    private fun readArray(id: Int, state: FormState<Field>, from: PanelArray?, reader: FieldReader<Field>):
        PanelArray? {
        val kept = kept(id)
        val entered = if (kept == null) entered(state, reader.problems) else null
        val azimuth = degrees(state, Field.DIRECTION, from?.azimuthDeg()) { point(it)?.azimuthDeg() }
        val tilt = degrees(state, Field.TILT, from?.tiltDeg()) { tiltChoice(it)?.degrees() }
        if (azimuth == null) {
            reader.problems[Field.DIRECTION] = FieldProblem.NotChosen
        }
        if (tilt == null) {
            reader.problems[Field.TILT] = FieldProblem.NotChosen
        }
        val losses = reader.number(Field.LOSSES, from?.systemLosses() ?: PanelArray.TYPICAL.systemLosses(),
            LOSSES_DECIMALS, scale = 100.0, untouchedIsStored = from != null)
        return reader.records({ setting -> Field.entries.first { it.setting == setting } }) {
            val size = kept ?: size(state, entered!!)
            PanelArray(state.text(Field.NAME), size, tilt!!, azimuth!!, losses)
        }
    }

    /**
     * The count, the year and the power as typed, or null with each one's problem noted: a count is needed, and a
     * year or a power that is typed must be a whole number.
     */
    private fun entered(state: FormState<Field>, problems: MutableMap<Field, FieldProblem>): Entered? {
        val count = whole(state, Field.COUNT, problems,
            empty = FieldProblem.Refused(InvalidSetting.Rule.PANEL_COUNT_OUT_OF_RANGE))
        val year = whole(state, Field.YEAR, problems, empty = null)
        if (year != null && (year < Year.MIN_VALUE || year > Year.MAX_VALUE)) {
            problems[Field.YEAR] = FieldProblem.Refused(InvalidSetting.Rule.YEAR_PLACED_OUT_OF_RANGE)
        }
        val power = whole(state, Field.POWER, problems, empty = null)
        return if (count == null || SIZE_FIELDS.any { it in problems }) null else Entered(count, year, power)
    }

    /**
     * A stored array the owner left alone is kept as read even with a year after this one, as the clock may be set
     * back or the file come from a time zone already in the new year; a typed year may not be after this year.
     */
    private fun size(state: FormState<Field>, entered: Entered): PanelSize.Panels =
        if (SIZE_FIELDS.any { state.isEdited(it) }) entered.size().notPlacedAfter(thisYear()) else entered.size()

    /**
     * @param empty the problem of an empty field; null when it may be empty
     * @return the number; null when the field is empty or wrong, a problem then noted where there is one
     */
    private fun whole(state: FormState<Field>, field: Field, problems: MutableMap<Field, FieldProblem>,
        empty: FieldProblem?): Int? {
        val text = state.text(field).trim()
        if (text.isEmpty()) {
            empty?.let { problems[field] = it }
            return null
        }
        return when (val read = numbers.readWhole(text)) {
            is NumberText.WholeNumber.Read -> read.value
            is NumberText.WholeNumber.Wrong -> null.also { problems[field] = read.problem }
        }
    }

    /** The owner's choice once made, else the stored degrees; null on a new array before a choice. */
    private fun degrees(state: FormState<Field>, field: Field, stored: Double?, of: (String) -> Double?): Double? =
        if (state.isEdited(field) || stored == null) of(state.text(field)) else stored

    // the names come from the saved state, which may hold one from another version of the app
    private fun point(name: String): CompassPoint? = CompassPoint.entries.firstOrNull { it.name == name }

    private fun tiltChoice(name: String): TiltChoice? = TiltChoice.entries.firstOrNull { it.name == name }

    private fun kept(row: Int): PanelSize.Peak? {
        val state = rows.state(row)
        val kwp = rows.decimal(row, KEPT_KWP) ?: return null
        if (SIZE_FIELDS.any { state.text(it).isNotBlank() }) {
            return null
        }
        return try {
            PanelSize.Peak(kwp)
        } catch (ignored: InvalidSetting) {
            null
        }
    }

    private fun marked(typical: Set<ConfigSetting>): Set<Field> =
        Field.entries.filter { field -> field.setting?.let { it in typical } == true }.toSet()

    private fun show(state: FormState<Field>, array: PanelArray) {
        val size = array.size() as? PanelSize.Panels
        state.fill(Field.COUNT, size?.count()?.toString().orEmpty())
        state.fill(Field.DIRECTION, CompassPoint.nearest(array.azimuthDeg()).name)
        state.fill(Field.TILT, TiltChoice.nearest(array.tiltDeg()).name)
        state.fill(Field.YEAR, size?.placed()?.map { it.value.toString() }?.orElse("").orEmpty())
        state.fill(Field.POWER, size?.wattPeak()?.takeIf { it.isPresent }?.asInt?.toString().orEmpty())
        state.fill(Field.LOSSES, numbers.write(array.systemLosses() * 100, LOSSES_DECIMALS))
        state.fill(Field.NAME, array.name())
    }

    private fun showNew(state: FormState<Field>, put: FormState<Field>.(Field, String) -> Unit) {
        Field.entries.forEach { state.put(it, "") }
        state.put(Field.LOSSES, numbers.write(PanelArray.TYPICAL.systemLosses() * 100, LOSSES_DECIMALS))
        state.markTypical(setOf(Field.LOSSES))
    }
}
