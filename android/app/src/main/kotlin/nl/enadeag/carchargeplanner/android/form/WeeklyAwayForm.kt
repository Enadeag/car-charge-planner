package nl.enadeag.carchargeplanner.android.form

import androidx.lifecycle.SavedStateHandle
import java.time.DayOfWeek
import java.time.LocalTime
import java.util.Locale
import kotlinx.coroutines.flow.StateFlow
import nl.enadeag.carchargeplanner.engine.away.WeeklyAwayPeriod
import nl.enadeag.carchargeplanner.engine.config.EngineConfig
import nl.enadeag.carchargeplanner.engine.config.WeeklyAwaySchedule

/**
 * The weekly away periods, possibly none, each a weekday and two times within that day, both chosen rather than typed.
 *
 * @param handle where the rows and their texts are kept
 */
class WeeklyAwayForm(private val handle: SavedStateHandle) : ConfigForm {

    /** A field of a period's row. */
    enum class Field {
        /** The weekday, chosen rather than typed. */
        WEEKDAY,
        /** The start, such as 08:00. */
        START,
        /** The end, after the start on the same day, which shows the problems of the period as a whole. */
        END,
    }

    private companion object {
        /** A new row, as the desktop adds one. */
        val NEW = WeeklyAwayPeriod(DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(17, 0))

        /** How many periods were loaded, so removing one counts as an edit. */
        const val LOADED = "away.loaded"

        /** No field here is a number; the reader, which has the record check a period, needs a writer all the same. */
        val NO_NUMBERS = NumberText(Locale.ROOT)
    }

    private val rows = Rows(handle, "away", Field.entries)

    /** The periods' rows, by number, in the order shown. */
    val rowIds: StateFlow<List<Int>> = rows.rows

    /**
     * A row's text.
     *
     * @param row the row's number
     * @param field the field
     * @return what it holds; the weekday's name, such as MONDAY, for the weekday
     */
    fun text(row: Int, field: Field): String = rows.state(row).text(field)

    /**
     * A row's time as it shows and is saved.
     *
     * @param row the row's number
     * @param field the start or the end
     * @return the time chosen; a new period's when the kept text is no time, as only an older version of the app can
     *     leave
     */
    fun time(row: Int, field: Field): LocalTime = TimeText.read(text(row, field))
        ?: if (field == Field.START) NEW.start() else NEW.end()

    /**
     * Chooses a row's start or end: the field's problem goes. Choosing the time shown changes nothing.
     *
     * @param row the row's number
     * @param field the start or the end
     * @param time the time, to the minute
     */
    fun choose(row: Int, field: Field, time: LocalTime) {
        val text = TimeText.write(time)
        if (text(row, field) != text) {
            rows.state(row).edit(field, text)
        }
    }

    /**
     * Chooses a row's weekday.
     *
     * @param row the row's number
     * @param weekday the day
     */
    fun choose(row: Int, weekday: DayOfWeek) = rows.state(row).edit(Field.WEEKDAY, weekday.name)

    /**
     * The problems a row's fields show now.
     *
     * @param row the row's number
     * @return its problems, by field
     */
    fun problems(row: Int): StateFlow<Map<Field, FieldProblem>> = rows.state(row).problems

    /**
     * Adds a period, Monday from 08:00 to 17:00.
     *
     * @return its row's number
     */
    fun add(): Int {
        val id = rows.add()
        show(id, NEW, FormState<Field>::replace)
        return id
    }

    /**
     * Removes a period.
     *
     * @param row the row's number
     */
    fun remove(row: Int) = rows.remove(row)

    override fun load(config: EngineConfig) {
        rows.clear()
        config.away().weekly().forEach { period -> show(rows.add(), period, FormState<Field>::fill) }
        handle[LOADED] = config.away().weekly().size
    }

    override fun read(config: EngineConfig): EngineConfig? = evaluate(config).first

    override fun check(config: EngineConfig): EngineConfig? {
        val (result, problems) = evaluate(config)
        rows.rows.value.forEach { id -> rows.state(id).show(problems[id].orEmpty()) }
        return result
    }

    override fun isEdited(): Boolean =
        rows.rows.value.any { rows.state(it).isEdited() } || rows.rows.value.size != (handle.get<Int>(LOADED) ?: 0)

    private fun evaluate(config: EngineConfig): Pair<EngineConfig?, Map<Int, Map<Field, FieldProblem>>> {
        val periods = mutableListOf<WeeklyAwayPeriod>()
        val problems = linkedMapOf<Int, Map<Field, FieldProblem>>()
        for (id in rows.rows.value) {
            val state = rows.state(id)
            val reader = FieldReader(state, NO_NUMBERS)
            val weekday = DayOfWeek.entries.firstOrNull { it.name == state.text(Field.WEEKDAY) } ?: NEW.weekday()
            val start = time(id, Field.START)
            val end = time(id, Field.END)
            val period = reader.records({ Field.END }) { WeeklyAwayPeriod(weekday, start, end) }
            if (period == null) problems[id] = reader.problems.toMap() else periods += period
        }
        return if (problems.isEmpty()) config.withAway(WeeklyAwaySchedule(periods)) to emptyMap()
        else null to problems
    }

    private fun show(id: Int, period: WeeklyAwayPeriod, put: FormState<Field>.(Field, String) -> Unit) {
        val state = rows.state(id)
        state.put(Field.WEEKDAY, period.weekday().name)
        state.put(Field.START, TimeText.write(period.start()))
        state.put(Field.END, TimeText.write(period.end()))
    }
}
