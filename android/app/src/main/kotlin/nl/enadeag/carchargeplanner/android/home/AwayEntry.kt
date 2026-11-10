package nl.enadeag.carchargeplanner.android.home

import androidx.lifecycle.SavedStateHandle
import java.time.Clock
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import nl.enadeag.carchargeplanner.android.form.forget
import nl.enadeag.carchargeplanner.android.store.AwaySave
import nl.enadeag.carchargeplanner.android.store.EngineAccess
import nl.enadeag.carchargeplanner.engine.away.OneOffAwayPeriod

/**
 * The dashboard's changes to the one-off away periods: the entry of one on a day of the plan, all day or part
 * of it, and Remove of a coming one with Undo. The entry opens on all day, with the times ready at 09:00 and 17:00;
 * Save refuses, as the desktop does, an end that is not after the start and a period that has passed by the clock at
 * Save, and nothing is saved then. The times are read in the home's time zone, not the phone's. A save that worked
 * closes the entry, says so once and hands the stored periods on; one that failed keeps the entry open with what was
 * entered and why. Remove acts at once and says so once, with the period, so Undo can add it back; a Remove or an
 * Undo that failed says so once with the engine's words. Only one change runs at a time: while one does, the others
 * do nothing, as the screen has them off, except an Undo, which waits for it and then runs, as its snackbar has
 * closed and would otherwise lose the period; when that change has something to say, the Undo waits until the screen
 * said it, so neither notice is lost. Back, or a tap beside the entry, asks before it throws changed times or
 * choice away; Cancel throws them away at once. While a save runs, nothing closes the entry. Whether it is open, its
 * day and zone, what was entered and the question survive turning the phone and the system ending the app in the
 * [SavedStateHandle], and are forgotten when it closes, or when they can no longer be read, such as a time zone that
 * an update removed; what a change came to and the problem shown are not kept.
 * Called from the main thread.
 *
 * @param handle the screen's saved state
 * @param access stores the changes
 * @param scope where the changes run, the home's
 * @param clock tells the time at Save, by which a period must not have ended
 * @param changed gets the stored periods after each change that worked, so the dashboard shows them and plans again
 */
class AwayEntry(private val handle: SavedStateHandle, private val access: EngineAccess,
    scope: CoroutineScope, private val clock: Clock,
    private val changed: (List<OneOffAwayPeriod>) -> Unit) {

    /** Whether the car is away the whole day or part of it. */
    enum class Kind {
        /** From the day's midnight to the next, so 23 or 25 hours on a day the clocks change. */
        ALL_DAY,

        /** From a start time to an end time on the day. */
        PART_OF_DAY,
    }

    /** Why Save refused what was entered. */
    enum class Problem {
        /** The end is not after the start, also when the clocks go forward between them so both are one moment. */
        END_NOT_AFTER_START,

        /** The period ended by the time of Save. */
        PASSED,
    }

    /**
     * What the open entry holds.
     *
     * @property day the day of the plan the car is away
     * @property zone the home's time zone, in which the times are read
     * @property kind all day or part of it
     * @property start the time it leaves, for part of the day
     * @property end the time it is back, for part of the day
     */
    data class Draft(val day: LocalDate, val zone: ZoneId, val kind: Kind, val start: LocalTime, val end: LocalTime) {

        /** True when the owner changed something since the entry opened, so Back asks before throwing it away. */
        val edited: Boolean get() = kind != Kind.ALL_DAY || start != FIRST_START || end != FIRST_END

        /**
         * The period entered, or why it cannot be saved.
         *
         * @param now the time of Save
         * @return the period, or the problem
         */
        fun check(now: Instant): Checked {
            val period = when (kind) {
                Kind.ALL_DAY -> OneOffAwayPeriod.allDay(day, zone)
                Kind.PART_OF_DAY -> try {
                    OneOffAwayPeriod.on(day, start, end, zone)
                } catch (_: IllegalArgumentException) {
                    // also when the clocks go forward between the two times, so both are the same moment or the
                    // start comes after the end
                    return Checked.Refused(Problem.END_NOT_AFTER_START)
                }
            }
            return if (period.end().isAfter(now)) Checked.Valid(period) else Checked.Refused(Problem.PASSED)
        }
    }

    /** What the entry holds, checked. */
    sealed interface Checked {

        /**
         * A period that can be saved.
         *
         * @property period the period entered
         */
        data class Valid(val period: OneOffAwayPeriod) : Checked

        /**
         * Why it cannot be saved.
         *
         * @property problem the problem
         */
        data class Refused(val problem: Problem) : Checked
    }

    /**
     * A period as the entry that saved it reads it, in the entry's zone and by the clock at Save.
     *
     * @property zone the home's time zone, in which the period is told
     * @property away the period by kind, its day named from the day of Save
     */
    data class Entered(val zone: ZoneId, val away: ComingAway)

    /** What a change came to, for the screen to say once. */
    sealed interface Notice {

        /**
         * A period was added, by Save or by Undo.
         *
         * @property period the period
         * @property entered how the entry that saved it reads the period, for a screen that has shown no plan yet to
         *     name it by; null for an Undo, which follows a plan that was shown
         */
        data class Saved(val period: OneOffAwayPeriod, val entered: Entered? = null) : Notice

        /**
         * A period was removed; Undo adds it back.
         *
         * @property period the period
         */
        data class Removed(val period: OneOffAwayPeriod) : Notice

        /**
         * Remove failed, so the period stays.
         *
         * @property detail the system's or the engine's words, in English
         */
        data class NotRemoved(val detail: String) : Notice

        /**
         * Undo failed, so the period stays removed.
         *
         * @property detail the system's or the engine's words, in English
         */
        data class NotSaved(val detail: String) : Notice
    }

    companion object {
        /** The start the entry opens with. */
        val FIRST_START: LocalTime = LocalTime.of(9, 0)

        /** The end the entry opens with. */
        val FIRST_END: LocalTime = LocalTime.of(17, 0)

        private const val PREFIX = "oneOffAway"
        private const val DAY = "$PREFIX.day"
        private const val ZONE = "$PREFIX.zone"
        private const val PART = "$PREFIX.part"
        private const val START = "$PREFIX.start"
        private const val END = "$PREFIX.end"
        private const val DISCARD = "$PREFIX.discard"

        /**
         * Forgets everything the entry kept in the handle, as the home it belongs to ends.
         *
         * @param handle the screen's saved state
         */
        fun forget(handle: SavedStateHandle) = handle.forget(listOf(PREFIX))
    }

    private val draftNow = MutableStateFlow(kept())
    private val problemNow = MutableStateFlow<Problem?>(null)
    private val changes = ChangeQueue<Notice>(scope)
    private val flow = EntryFlow(handle, DISCARD, changes, { draftNow.value != null },
        { draftNow.value?.edited == true }, ::shut)

    /** What the open entry holds; null while it is closed. */
    val draft: StateFlow<Draft?> = draftNow.asStateFlow()

    /** Why Save refused what was entered, until it is changed; null when nothing is wrong. */
    val problem: StateFlow<Problem?> = problemNow.asStateFlow()

    /** True while Back asks whether to throw what was entered away. */
    val discardAsked: StateFlow<Boolean> = flow.discardAsked

    /** Where the entry's Save is. */
    val saving: StateFlow<SaveState> = flow.saving

    /** True while a change runs: a save, a Remove or an Undo; Add and Remove are off then. */
    val busy: StateFlow<Boolean> = changes.busy

    /** What the last change came to, until the screen said so, as [noticeShown] tells; null when nothing is to say. */
    val notice: StateFlow<Notice?> = changes.notice

    /**
     * Opens the entry on a day, all day with the times ready at [FIRST_START] and [FIRST_END]; one already open stays
     * as it is, and while a change runs it does nothing.
     *
     * @param day the day of the plan the car is away
     * @param zone the home's time zone, in which the times are read
     */
    fun open(day: LocalDate, zone: ZoneId) {
        if (draftNow.value != null || changes.busy.value) {
            return
        }
        handle[DAY] = day.toString()
        handle[ZONE] = zone.id
        put(Draft(day, zone, Kind.ALL_DAY, FIRST_START, FIRST_END))
    }

    /**
     * Chooses all day or part of it; the problem shown goes.
     *
     * @param kind the choice
     */
    fun choose(kind: Kind) = change { it.copy(kind = kind) }

    /**
     * Puts the time the car leaves; the problem shown goes.
     *
     * @param time the time, on the entry's day
     */
    fun start(time: LocalTime) = change { it.copy(start = time) }

    /**
     * Puts the time the car is back; the problem shown goes.
     *
     * @param time the time, on the entry's day
     */
    fun end(time: LocalTime) = change { it.copy(end = time) }

    /** Saves the period entered, or shows why it cannot be saved. While a change runs it does nothing. */
    fun save() {
        val draft = draftNow.value ?: return
        flow.save({ checked(draft) }) { period ->
            when (val outcome = access.addAwayPeriod(period)) {
                is AwaySave.Saved -> {
                    changes.say(Notice.Saved(period, Entered(draft.zone,
                        ComingAway.describe(period, draft.zone, clock.instant()))))
                    changed(outcome.periods)
                    null
                }
                is AwaySave.Failed -> outcome.detail
            }
        }
    }

    /**
     * Removes a coming period at once. While a change runs, or the entry is open, it does nothing.
     *
     * @param period the period as the dashboard lists it
     */
    fun remove(period: OneOffAwayPeriod) {
        if (draftNow.value == null) {
            run(period, access::removeAwayPeriod, { Notice.Removed(period) }, Notice::NotRemoved)
        }
    }

    /**
     * Adds a removed period back, as Undo does. While a change runs it waits for it, and for the screen to say what
     * it came to, and then runs, also when the entry is open by then; while the entry is open and no change runs it
     * does nothing.
     *
     * @param period the period that was removed
     */
    fun undo(period: OneOffAwayPeriod) {
        when {
            changes.busy.value -> changes.later { restore(period) }
            draftNow.value == null -> restore(period)
        }
    }

    /** Cancel: closes the entry and throws away what was entered; while a save runs it does nothing. */
    fun cancel() = flow.discard()

    /**
     * Back, or a tap beside the entry: with something changed, asks whether to throw it away; without, closes. While
     * a save runs it does nothing.
     */
    fun back() = flow.back()

    /** Throws what was entered away and closes, as the owner chose Discard; while a save runs it does nothing. */
    fun discard() = flow.discard()

    /** Stays in the entry with what was entered, as the owner chose Keep editing. */
    fun keepEditing() = flow.keepEditing()

    /**
     * The screen said what the last change came to, so it is not said again after turning; an Undo that waited for
     * that change runs now.
     */
    fun noticeShown() = changes.noticeShown()

    private fun restore(period: OneOffAwayPeriod) {
        run(period, access::addAwayPeriod, { Notice.Saved(period) }, Notice::NotSaved)
    }

    private fun run(period: OneOffAwayPeriod, store: suspend (OneOffAwayPeriod) -> AwaySave, saved: () -> Notice,
        failed: (String) -> Notice) {
        changes.run({
            when (val outcome = store(period)) {
                is AwaySave.Saved -> {
                    changes.say(saved())
                    changed(outcome.periods)
                }
                is AwaySave.Failed -> changes.say(failed(outcome.detail))
            }
        })
    }

    /** The period entered; null when Save refuses it, its problem then shown. */
    private fun checked(draft: Draft): OneOffAwayPeriod? = when (val checked = draft.check(clock.instant())) {
        is Checked.Refused -> {
            problemNow.value = checked.problem
            null
        }
        is Checked.Valid -> checked.period
    }

    private fun change(edit: (Draft) -> Draft) {
        val draft = draftNow.value ?: return
        if (!flow.takesEdits) {
            return
        }
        problemNow.value = null
        put(edit(draft))
    }

    private fun put(draft: Draft) {
        handle[PART] = draft.kind == Kind.PART_OF_DAY
        handle[START] = draft.start.toSecondOfDay()
        handle[END] = draft.end.toSecondOfDay()
        draftNow.value = draft
    }

    private fun kept(): Draft? {
        val day = handle.get<String>(DAY) ?: return null
        val zone = handle.get<String>(ZONE) ?: return null
        return try {
            Draft(LocalDate.parse(day), ZoneId.of(zone),
                if (handle.get<Boolean>(PART) == true) Kind.PART_OF_DAY else Kind.ALL_DAY,
                handle.get<Int>(START)?.let { LocalTime.ofSecondOfDay(it.toLong()) } ?: FIRST_START,
                handle.get<Int>(END)?.let { LocalTime.ofSecondOfDay(it.toLong()) } ?: FIRST_END)
        } catch (_: DateTimeException) {
            // a zone a time-zone update removed, or a value that is no date or time: the entry is closed
            forget(handle)
            null
        }
    }

    private fun shut() {
        forget(handle)
        problemNow.value = null
        draftNow.value = null
    }
}
