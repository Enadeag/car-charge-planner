package nl.enadeag.carchargeplanner.android.home

import androidx.lifecycle.SavedStateHandle
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import nl.enadeag.carchargeplanner.android.form.NumberText
import nl.enadeag.carchargeplanner.android.store.EngineAccess
import nl.enadeag.carchargeplanner.android.store.TripSave
import nl.enadeag.carchargeplanner.engine.trip.Trip

/**
 * The dashboard's changes to the trips: the entry of a new trip (Add) or of a change to one (Change), a trip's two
 * choices, "Not with this car" and "I can charge at the destination", each saved at once as the owner changes it, and
 * Remove of a trip with Undo, as the one-off away periods' ([AwayEntry]). A choice shows its new state at once while
 * it is saved; one whose save failed springs back and says so once with the engine's words; one saved says nothing, as
 * the choice shows it. Remove acts at once and says so once, with the trip, so Undo can add it back; a Remove or an
 * Undo that failed says so once with the engine's words. Only one change runs at a time: while one does, a choice, a
 * Remove or the entry's Save does nothing, as the screen has the controls off, and an Undo waits for it and then runs,
 * as its snackbar has closed and would otherwise lose the trip; when that change has something to say, the Undo waits
 * until the screen said it, so neither notice is lost.
 *
 * The entry works as the desktop's, in the home's time zone: a new trip opens on the page's day, from 08:00 to 18:00,
 * with no distance and "I can charge at the destination" off; a change opens on the trip as it is, its times to the
 * minute, its distance in whole km and "Not with this car" carried along unseen. The days offered run from the home's
 * today, by the clock when the entry opens, to 20 days after it, widened to the days the entry opened on. A time the
 * clocks skip counts as the time after the gap, one they repeat as its first. Save checks, in the desktop's order,
 * that the return is after the departure, that it has not passed by the clock at Save, and that the distance is a
 * number more than 0, read as a number field reads it; it shows the first problem only, which goes at the next change.
 * A distance left as it first showed keeps the trip's own, so a change does not round it. A save that worked closes
 * the entry, says so once and hands the stored trips on; one that failed keeps the entry open with what was entered
 * and why. Back, or Close, asks before it throws a change away and closes at once when nothing differs from how the
 * entry opened; while a save runs, nothing closes it. What is entered, how the entry opened and the question survive
 * turning the phone and the system ending the app in the [SavedStateHandle], and are forgotten when it closes, or when
 * they can no longer be read; the problem shown and what a save came to are not kept.
 * Called from the main thread.
 *
 * @param handle the screen's saved state
 * @param numbers reads the distance typed, and writes a trip's distance as the entry first shows it
 * @param access stores the changes
 * @param scope where the changes run, the home's
 * @param clock tells the home's today when the entry opens, and the time at Save, by which a trip must not be over
 * @param changed gets the stored trips after each change that worked, so the dashboard shows them and plans again
 */
class TripEntry(private val handle: SavedStateHandle, private val numbers: NumberText,
    private val access: EngineAccess, scope: CoroutineScope, private val clock: Clock,
    private val changed: (List<Trip>) -> Unit) {

    /**
     * A choice being saved, which the row shows at once in its new state.
     *
     * @property trip the trip as the dashboard listed it, before the change
     * @property choice the choice
     * @property on its new state
     */
    data class Pending(val trip: Trip, val choice: TripChoice, val on: Boolean)

    /** Where the owner fixes a problem. */
    enum class Field {
        /** The return's day and time. */
        RETURN,

        /** The distance there and back. */
        DISTANCE,
    }

    /**
     * A trip as the entry that saved it reads it, in the entry's zone and by the clock at Save.
     *
     * @property zone the home's time zone, in which the trip is told
     * @property trip the trip with its days, named from the day of Save
     */
    data class Entered(val zone: ZoneId, val trip: ListedTrip)

    /** What a change came to, for the screen to say once. */
    sealed interface Notice {

        /**
         * A trip was saved by the entry, or a removed one added back by Undo.
         *
         * @property trip the trip
         * @property entered how the entry that saved it reads the trip, for a screen that has shown no plan yet to
         *     name it by; null for an Undo, which follows a plan that was shown
         */
        data class Saved(val trip: Trip, val entered: Entered? = null) : Notice

        /**
         * A trip was removed; Undo adds it back.
         *
         * @property trip the trip
         */
        data class Removed(val trip: Trip) : Notice

        /**
         * Remove failed, so the trip stays.
         *
         * @property detail the system's or the engine's words, in English
         */
        data class NotRemoved(val detail: String) : Notice

        /**
         * A choice or an Undo failed, so the trip stays as it was, or removed.
         *
         * @property detail the system's or the engine's words, in English
         */
        data class NotSaved(val detail: String) : Notice
    }

    companion object {
        /**
         * Forgets everything the entry kept in the handle, as the home it belongs to ends.
         *
         * @param handle the screen's saved state
         */
        fun forget(handle: SavedStateHandle) = TripDraftKept.forget(handle)
    }

    private val changes = ChangeQueue<Notice>(scope)
    private val kept = TripDraftKept(handle)
    private val pendingNow = MutableStateFlow<Pending?>(null)
    private val draftNow = MutableStateFlow(kept.kept())
    private val problemNow = MutableStateFlow<TripDraft.Problem?>(null)
    private val flow = EntryFlow(handle, TripDraftKept.DISCARD, changes, { draftNow.value != null },
        { draftNow.value?.edited == true }, ::closed)

    /**
     * The trip whose Change the focus goes back to once the entry closed: the trip as saved after a change, the trip
     * as it opened when a change closed without saving; null after a new trip, whose Add takes the focus.
     */
    var closedOn: Trip? = null
        private set

    /** The trip a save that worked stored, until the entry closes on it. */
    private var savedTrip: Trip? = null

    /** True while a change runs: a choice, a Remove, an Undo or the entry's save; the card's controls are off then. */
    val busy: StateFlow<Boolean> = changes.busy

    /** The choice being saved; null while none is. */
    val pending: StateFlow<Pending?> = pendingNow.asStateFlow()

    /** What the last change came to, until the screen said so, as [noticeShown] tells; null when nothing is to say. */
    val notice: StateFlow<Notice?> = changes.notice

    /** What the open entry holds; null while it is closed. */
    val draft: StateFlow<TripDraft?> = draftNow.asStateFlow()

    /** Why Save refused what was entered, until something is changed; null when nothing is wrong. */
    val problem: StateFlow<TripDraft.Problem?> = problemNow.asStateFlow()

    /** How many times Save refused what was entered, so the screen moves the focus to the problem each time. */
    val refusals: StateFlow<Int> = flow.refusals

    /** True while Back asks whether to throw what was entered away. */
    val discardAsked: StateFlow<Boolean> = flow.discardAsked

    /** Where the entry's Save is. */
    val saving: StateFlow<SaveState> = flow.saving

    /**
     * Opens the entry of a new trip on a day, with the fields of [TripDraft.Fields.new]. One already open stays as it
     * is, and while a change runs it does nothing.
     *
     * @param day the page's day, on which the trip leaves and returns
     * @param zone the home's time zone, in which the days and times are read
     */
    fun add(day: LocalDate, zone: ZoneId) {
        open(TripDraft.Mode.ADD, zone, TripDraft.Fields.new(day), null)
    }

    /**
     * Opens the entry on a stored trip, to change it: its days and times in the home's time zone, to the minute, its
     * distance in whole km and its choices as stored. One already open stays as it is, and while a change runs it
     * does nothing.
     *
     * @param trip the trip as the dashboard lists it
     * @param zone the home's time zone, in which the days and times are read
     */
    fun change(trip: Trip, zone: ZoneId) {
        open(TripDraft.Mode.CHANGE, zone, TripDraft.Fields.of(trip, zone, numbers.write(trip.distanceKm(), 0)), trip)
    }

    /**
     * Puts the day the car leaves; the problem shown goes.
     *
     * @param day the day, one of [TripDraft.days]
     */
    fun departureDay(day: LocalDate) = edit { it.copy(departureDay = day) }

    /**
     * Puts the time the car leaves; the problem shown goes.
     *
     * @param time the time, of which the hours and minutes count
     */
    fun departureTime(time: LocalTime) = edit { it.copy(departureTime = time.truncatedTo(ChronoUnit.MINUTES)) }

    /**
     * Puts the day the car is back; the problem shown goes. The entry never moves it itself.
     *
     * @param day the day, one of [TripDraft.days]
     */
    fun returnDay(day: LocalDate) = edit { it.copy(returnDay = day) }

    /**
     * Puts the time the car is back; the problem shown goes.
     *
     * @param time the time, of which the hours and minutes count
     */
    fun returnTime(time: LocalTime) = edit { it.copy(returnTime = time.truncatedTo(ChronoUnit.MINUTES)) }

    /**
     * Puts the distance typed; the problem shown goes.
     *
     * @param text what the field holds, of which the first [TripDraft.Fields.DISTANCE_LENGTH] characters are kept
     */
    fun distance(text: String) = edit { it.copy(distance = text.take(TripDraft.Fields.DISTANCE_LENGTH)) }

    /**
     * Puts whether the owner can charge the car where the trip goes; the problem shown goes.
     *
     * @param on whether it is ticked
     */
    fun canCharge(on: Boolean) = edit { it.copy(canCharge = on) }

    /**
     * Saves the trip entered, a new one or the change, or shows why it cannot be saved. While a change of the trips
     * runs it does nothing.
     */
    fun save() {
        val draft = draftNow.value ?: return
        val opened = draft.opened
        flow.save({ checked(draft) }) { trip ->
            when (val outcome = if (opened == null) access.addTrip(trip) else access.changeTrip(opened, trip)) {
                is TripSave.Saved -> {
                    savedTrip = trip
                    changes.say(Notice.Saved(trip, Entered(draft.zone, ListedTrip.of(trip, draft.zone,
                        clock.instant()))))
                    changed(outcome.trips)
                    null
                }
                is TripSave.Failed -> outcome.detail
            }
        }
    }

    /**
     * Back, or the entry's Close: with something changed, asks whether to throw it away; without, closes. While a
     * save runs it does nothing.
     */
    fun back() = flow.back()

    /** Throws what was entered away and closes, as the owner chose Discard; while a save runs it does nothing. */
    fun discard() = flow.discard()

    /** Stays in the entry with what was entered, as the owner chose Keep editing. */
    fun keepEditing() = flow.keepEditing()

    /**
     * Puts one of a trip's choices on or off and saves it at once. While a change runs it does nothing.
     *
     * @param trip the trip as the dashboard lists it
     * @param choice the choice
     * @param on its new state
     */
    fun choose(trip: Trip, choice: TripChoice, on: Boolean) {
        if (changes.busy.value) {
            return
        }
        pendingNow.value = Pending(trip, choice, on)
        run({ access.changeTrip(trip, choice.put(trip, on)) }, null, Notice::NotSaved)
    }

    /**
     * Removes a trip at once. While a change runs it does nothing.
     *
     * @param trip the trip as the dashboard lists it
     */
    fun remove(trip: Trip) {
        if (!changes.busy.value) {
            run({ access.removeTrip(trip) }, Notice.Removed(trip), Notice::NotRemoved)
        }
    }

    /**
     * Adds a removed trip back, as Undo does. While a change runs it waits for it, and for the screen to say what it
     * came to, and then runs.
     *
     * @param trip the trip that was removed
     */
    fun undo(trip: Trip) {
        if (changes.busy.value) {
            changes.later { restore(trip) }
        } else {
            restore(trip)
        }
    }

    /**
     * The screen said what the last change came to, so it is not said again after turning; an Undo that waited for
     * that change runs now.
     */
    fun noticeShown() = changes.noticeShown()

    private fun restore(trip: Trip) = run({ access.addTrip(trip) }, Notice.Saved(trip), Notice::NotSaved)

    private fun run(store: suspend () -> TripSave, saved: Notice?, failed: (String) -> Notice) {
        changes.run({
            when (val outcome = store()) {
                is TripSave.Saved -> {
                    saved?.let { changes.say(it) }
                    changed(outcome.trips)
                }
                is TripSave.Failed -> changes.say(failed(outcome.detail))
            }
        }, done = { pendingNow.value = null })
    }

    private fun open(mode: TripDraft.Mode, zone: ZoneId, first: TripDraft.Fields, trip: Trip?) {
        if (draftNow.value != null || changes.busy.value) {
            return
        }
        val draft = TripDraft(mode, zone, clock.instant().atZone(zone).toLocalDate(), first, trip, first)
        kept.opened(draft)
        closedOn = null
        draftNow.value = draft
    }

    /** The trip entered; null when Save refuses it, its problem then shown. */
    private fun checked(draft: TripDraft): Trip? = when (val checked = draft.check(clock.instant(), numbers)) {
        is TripDraft.Checked.Refused -> {
            problemNow.value = checked.problem
            null
        }
        is TripDraft.Checked.Valid -> checked.trip
    }

    private fun edit(change: (TripDraft.Fields) -> TripDraft.Fields) {
        val draft = draftNow.value ?: return
        if (!flow.takesEdits) {
            return
        }
        problemNow.value = null
        val fields = change(draft.fields)
        kept.put(fields)
        draftNow.value = draft.copy(fields = fields)
    }

    /**
     * Closes the entry; the focus goes back to the Change of the trip it opened on, if any: the trip as saved after a
     * save, as it opened without one.
     */
    private fun closed() {
        forget(handle)
        closedOn = draftNow.value?.opened?.let { savedTrip ?: it }
        savedTrip = null
        problemNow.value = null
        draftNow.value = null
    }
}
