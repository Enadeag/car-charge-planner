package nl.enadeag.carchargeplanner.android.home

import androidx.lifecycle.SavedStateHandle
import java.time.Clock
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import nl.enadeag.carchargeplanner.android.form.NumberText
import nl.enadeag.carchargeplanner.android.form.forget
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

    /** One of a trip's two choices. */
    enum class Choice {
        /** The owner makes the trip without this car, so it does not count for the plan. */
        NOT_WITH_THIS_CAR,

        /** The owner can charge the car where the trip goes. */
        CAN_CHARGE,
        ;

        /**
         * Whether a trip has this choice on.
         *
         * @param trip the trip
         * @return true when it is ticked
         */
        fun of(trip: Trip): Boolean = when (this) {
            NOT_WITH_THIS_CAR -> trip.notWithThisCar()
            CAN_CHARGE -> trip.canChargeAtDestination()
        }

        /**
         * The trip with this choice put on or off, the rest as it was.
         *
         * @param trip the trip
         * @param on whether the choice is ticked
         * @return the changed trip
         */
        fun put(trip: Trip, on: Boolean): Trip = when (this) {
            NOT_WITH_THIS_CAR -> Trip(trip.departure(), trip.returnTime(), trip.distanceKm(),
                trip.canChargeAtDestination(), on)
            CAN_CHARGE -> Trip(trip.departure(), trip.returnTime(), trip.distanceKm(), on, trip.notWithThisCar())
        }
    }

    /**
     * A choice being saved, which the row shows at once in its new state.
     *
     * @property trip the trip as the dashboard listed it, before the change
     * @property choice the choice
     * @property on its new state
     */
    data class Pending(val trip: Trip, val choice: Choice, val on: Boolean)

    /** Whether the entry enters a new trip or changes one. */
    enum class Mode {
        /** A new trip, from Add. */
        ADD,

        /** A change to a stored trip, from its Change. */
        CHANGE,
    }

    /** Where the owner fixes a problem. */
    enum class Field {
        /** The return's day and time. */
        RETURN,

        /** The distance there and back. */
        DISTANCE,
    }

    /**
     * Why Save refused what was entered, in the order the entry checks.
     *
     * @property field where the owner fixes it
     */
    enum class Problem(val field: Field) {
        /** The return is not after the departure, also when a time the clocks skip moves the departure on. */
        RETURN_NOT_AFTER_DEPARTURE(Field.RETURN),

        /** The return has passed by the time of Save. */
        PASSED(Field.RETURN),

        /** The distance is no number, or too large to be one. */
        DISTANCE_NOT_A_NUMBER(Field.DISTANCE),

        /** The distance is 0 or less. */
        DISTANCE_NOT_POSITIVE(Field.DISTANCE),
    }

    /**
     * What the owner can change in the entry.
     *
     * @property departureDay the day the car leaves, in the home's time zone
     * @property departureTime the time it leaves, to the minute
     * @property returnDay the day it is back
     * @property returnTime the time it is back, to the minute
     * @property distance the distance there and back as typed, in km
     * @property canCharge whether the owner can charge the car where the trip goes
     */
    data class Fields(val departureDay: LocalDate, val departureTime: LocalTime, val returnDay: LocalDate,
        val returnTime: LocalTime, val distance: String, val canCharge: Boolean)

    /**
     * What the open entry holds.
     *
     * @property mode a new trip or a change
     * @property zone the home's time zone, in which the days and times are read
     * @property today the home's today by the clock when the entry opened, which names the days and starts those
     *     offered
     * @property first the fields as the entry opened, its distance as it first showed
     * @property opened the trip as the entry opened it, exactly as stored; null for a new trip
     * @property fields the fields as they are now
     */
    data class Draft(val mode: Mode, val zone: ZoneId, val today: LocalDate, val first: Fields, val opened: Trip?,
        val fields: Fields) {

        /**
         * The days offered for the departure and the return, as the desktop's: from today to 20 days after it, widened
         * to the days the entry opened on, so a changed trip's own days stay offered.
         */
        val days: ClosedRange<LocalDate>
            get() = minOf(today, first.departureDay)..maxOf(today.plusDays(DAYS_OFFERED - 1L), first.returnDay)

        /**
         * True when something differs from how the entry opened, so Back asks before throwing it away; spaces around
         * the distance do not count.
         */
        val edited: Boolean get() = fields.copy(distance = fields.distance.trim()) != first

        /**
         * The trip entered, or why it cannot be saved.
         *
         * @param now the time of Save
         * @param numbers reads the distance typed
         * @return the trip, with "Not with this car" as the trip opened had it; or the first problem
         */
        fun check(now: Instant, numbers: NumberText): Checked {
            // a time the clocks skip counts as the time after the gap, one they repeat as its first
            val departure = fields.departureDay.atTime(fields.departureTime).atZone(zone).toInstant()
            val back = fields.returnDay.atTime(fields.returnTime).atZone(zone).toInstant()
            if (!back.isAfter(departure)) {
                return Checked.Refused(Problem.RETURN_NOT_AFTER_DEPARTURE)
            }
            if (!back.isAfter(now)) {
                return Checked.Refused(Problem.PASSED)
            }
            val typed = fields.distance.trim()
            // a distance left as it first showed, in whole km, stays the trip's own
            val km = opened?.takeIf { typed == first.distance }?.distanceKm()
                ?: numbers.read(typed) ?: return Checked.Refused(Problem.DISTANCE_NOT_A_NUMBER)
            if (km <= 0) {
                return Checked.Refused(Problem.DISTANCE_NOT_POSITIVE)
            }
            return Checked.Valid(Trip(departure, back, km, fields.canCharge, opened?.notWithThisCar() == true))
        }
    }

    /** What the entry holds, checked. */
    sealed interface Checked {

        /**
         * A trip that can be saved.
         *
         * @property trip the trip entered
         */
        data class Valid(val trip: Trip) : Checked

        /**
         * Why it cannot be saved.
         *
         * @property problem the first problem
         */
        data class Refused(val problem: Problem) : Checked
    }

    /**
     * A trip as the entry that saved it reads it, in the entry's zone and by the clock at Save.
     *
     * @property zone the home's time zone, in which the trip is told
     * @property trip the trip with its days, named from the day of Save
     */
    data class Entered(val zone: ZoneId, val trip: ListedTrip)

    /** Where the entry's Save is. */
    sealed interface SaveState {

        /** Not saving, and no save failed since the entry opened or the last save. */
        data object Idle : SaveState

        /** The trip is being saved; Save, Close and Back wait. */
        data object Saving : SaveState

        /**
         * The trip could not be stored; the entry keeps what was entered.
         *
         * @property detail the system's or the engine's words, in English
         */
        data class Failed(val detail: String) : SaveState
    }

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
        /** How many days, from today, the entry offers, as the desktop does. */
        const val DAYS_OFFERED = 21

        /** The departure time a new trip opens with, the desktop's. */
        val FIRST_DEPARTURE: LocalTime = LocalTime.of(8, 0)

        /** The return time a new trip opens with, the desktop's. */
        val FIRST_RETURN: LocalTime = LocalTime.of(18, 0)

        /** The most characters the distance takes, more than any distance needs. */
        const val DISTANCE_LENGTH = 16

        private const val PREFIX = "tripEntry"
        private const val MODE = "$PREFIX.mode"
        private const val ZONE = "$PREFIX.zone"
        private const val TODAY = "$PREFIX.today"
        private const val FIRST_DAY = "$PREFIX.firstDay"
        private const val OPENED_DEPARTURE = "$PREFIX.opened.departure"
        private const val OPENED_RETURN = "$PREFIX.opened.return"
        private const val OPENED_KM = "$PREFIX.opened.km"
        private const val OPENED_CAN_CHARGE = "$PREFIX.opened.canCharge"
        private const val OPENED_NOT_WITH = "$PREFIX.opened.notWithThisCar"
        private const val FIRST_DISTANCE = "$PREFIX.firstDistance"
        private const val DEPARTURE_DAY = "$PREFIX.departureDay"
        private const val DEPARTURE_TIME = "$PREFIX.departureTime"
        private const val RETURN_DAY = "$PREFIX.returnDay"
        private const val RETURN_TIME = "$PREFIX.returnTime"
        private const val DISTANCE = "$PREFIX.distance"
        private const val CAN_CHARGE = "$PREFIX.canCharge"
        private const val DISCARD = "$PREFIX.discard"

        /**
         * Forgets everything the entry kept in the handle, as the home it belongs to ends.
         *
         * @param handle the screen's saved state
         */
        fun forget(handle: SavedStateHandle) = handle.forget(listOf(PREFIX))
    }

    private val changes = ChangeQueue<Notice>(scope)
    private val pendingNow = MutableStateFlow<Pending?>(null)
    private val draftNow = MutableStateFlow(kept())
    private val problemNow = MutableStateFlow<Problem?>(null)
    private val refusalsNow = MutableStateFlow(0)
    private val discardNow = MutableStateFlow(draftNow.value != null && handle.get<Any?>(DISCARD) == true)
    private val savingNow = MutableStateFlow<SaveState>(SaveState.Idle)

    /**
     * The trip whose Change the focus goes back to once the entry closed: the trip as saved after a change, the trip
     * as it opened when a change closed without saving; null after a new trip, whose Add takes the focus.
     */
    var closedOn: Trip? = null
        private set

    /** True while a change runs: a choice, a Remove, an Undo or the entry's save; the card's controls are off then. */
    val busy: StateFlow<Boolean> = changes.busy

    /** The choice being saved; null while none is. */
    val pending: StateFlow<Pending?> = pendingNow.asStateFlow()

    /** What the last change came to, until the screen said so, as [noticeShown] tells; null when nothing is to say. */
    val notice: StateFlow<Notice?> = changes.notice

    /** What the open entry holds; null while it is closed. */
    val draft: StateFlow<Draft?> = draftNow.asStateFlow()

    /** Why Save refused what was entered, until something is changed; null when nothing is wrong. */
    val problem: StateFlow<Problem?> = problemNow.asStateFlow()

    /** How many times Save refused what was entered, so the screen moves the focus to the problem each time. */
    val refusals: StateFlow<Int> = refusalsNow.asStateFlow()

    /** True while Back asks whether to throw what was entered away. */
    val discardAsked: StateFlow<Boolean> = discardNow.asStateFlow()

    /** Where the entry's Save is. */
    val saving: StateFlow<SaveState> = savingNow.asStateFlow()

    /**
     * Opens the entry of a new trip on a day: both days that day, from [FIRST_DEPARTURE] to [FIRST_RETURN], with no
     * distance and "I can charge at the destination" off. One already open stays as it is, and while a change runs
     * it does nothing.
     *
     * @param day the page's day, on which the trip leaves and returns
     * @param zone the home's time zone, in which the days and times are read
     */
    fun add(day: LocalDate, zone: ZoneId) {
        open(Mode.ADD, zone, Fields(day, FIRST_DEPARTURE, day, FIRST_RETURN, "", false), null)
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
        open(Mode.CHANGE, zone, fieldsOf(trip, zone, numbers.write(trip.distanceKm(), 0)), trip)
    }

    /**
     * Puts the day the car leaves; the problem shown goes.
     *
     * @param day the day, one of [Draft.days]
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
     * @param day the day, one of [Draft.days]
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
     * @param text what the field holds, of which the first [DISTANCE_LENGTH] characters are kept
     */
    fun distance(text: String) = edit { it.copy(distance = text.take(DISTANCE_LENGTH)) }

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
        if (changes.busy.value) {
            return
        }
        val trip = when (val checked = draft.check(clock.instant(), numbers)) {
            is Checked.Refused -> {
                problemNow.value = checked.problem
                refusalsNow.value++
                return
            }
            is Checked.Valid -> checked.trip
        }
        savingNow.value = SaveState.Saving
        val opened = draft.opened
        changes.run({
            when (val outcome = if (opened == null) access.addTrip(trip) else access.changeTrip(opened, trip)) {
                is TripSave.Saved -> {
                    close(trip.takeIf { opened != null })
                    changes.say(Notice.Saved(trip, Entered(draft.zone,
                        ListedTrip.of(trip, draft.zone, clock.instant()))))
                    changed(outcome.trips)
                }
                is TripSave.Failed -> savingNow.value = SaveState.Failed(outcome.detail)
            }
        })
    }

    /**
     * Back, or the entry's Close: with something changed, asks whether to throw it away; without, closes. While a
     * save runs it does nothing.
     */
    fun back() {
        val draft = draftNow.value ?: return
        when {
            savingNow.value == SaveState.Saving -> Unit
            draft.edited -> ask(true)
            else -> close(draft.opened)
        }
    }

    /** Throws what was entered away and closes, as the owner chose Discard; while a save runs it does nothing. */
    fun discard() {
        val draft = draftNow.value ?: return
        if (savingNow.value != SaveState.Saving) {
            close(draft.opened)
        }
    }

    /** Stays in the entry with what was entered, as the owner chose Keep editing. */
    fun keepEditing() = ask(false)

    /**
     * Puts one of a trip's choices on or off and saves it at once. While a change runs it does nothing.
     *
     * @param trip the trip as the dashboard lists it
     * @param choice the choice
     * @param on its new state
     */
    fun choose(trip: Trip, choice: Choice, on: Boolean) {
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

    private fun open(mode: Mode, zone: ZoneId, first: Fields, trip: Trip?) {
        if (draftNow.value != null || changes.busy.value) {
            return
        }
        val today = clock.instant().atZone(zone).toLocalDate()
        handle[MODE] = mode.name
        handle[ZONE] = zone.id
        handle[TODAY] = today.toString()
        if (trip == null) {
            handle[FIRST_DAY] = first.departureDay.toString()
        } else {
            handle[OPENED_DEPARTURE] = trip.departure().toString()
            handle[OPENED_RETURN] = trip.returnTime().toString()
            handle[OPENED_KM] = trip.distanceKm().toString()
            handle[OPENED_CAN_CHARGE] = trip.canChargeAtDestination()
            handle[OPENED_NOT_WITH] = trip.notWithThisCar()
            handle[FIRST_DISTANCE] = first.distance
        }
        closedOn = null
        put(Draft(mode, zone, today, first, trip, first))
    }

    /** A stored trip's fields as the entry opens on them, its distance as written at the open. */
    private fun fieldsOf(trip: Trip, zone: ZoneId, distance: String): Fields {
        val leaves = trip.departure().atZone(zone)
        val back = trip.returnTime().atZone(zone)
        return Fields(leaves.toLocalDate(), leaves.toLocalTime().truncatedTo(ChronoUnit.MINUTES), back.toLocalDate(),
            back.toLocalTime().truncatedTo(ChronoUnit.MINUTES), distance, trip.canChargeAtDestination())
    }

    private fun edit(change: (Fields) -> Fields) {
        val draft = draftNow.value ?: return
        if (savingNow.value == SaveState.Saving) {
            return
        }
        problemNow.value = null
        put(draft.copy(fields = change(draft.fields)))
    }

    private fun put(draft: Draft) {
        val fields = draft.fields
        handle[DEPARTURE_DAY] = fields.departureDay.toString()
        handle[DEPARTURE_TIME] = fields.departureTime.toSecondOfDay()
        handle[RETURN_DAY] = fields.returnDay.toString()
        handle[RETURN_TIME] = fields.returnTime.toSecondOfDay()
        handle[DISTANCE] = fields.distance
        handle[CAN_CHARGE] = fields.canCharge
        draftNow.value = draft
    }

    private fun kept(): Draft? {
        if (handle.get<Any?>(MODE) == null) {
            return null
        }
        return try {
            val mode = Mode.valueOf(text(MODE))
            val zone = ZoneId.of(text(ZONE))
            val opened = if (mode == Mode.CHANGE) Trip(
                Instant.parse(text(OPENED_DEPARTURE)), Instant.parse(text(OPENED_RETURN)), text(OPENED_KM).toDouble(),
                flag(OPENED_CAN_CHARGE), flag(OPENED_NOT_WITH)) else null
            val first = if (opened == null) {
                val day = LocalDate.parse(text(FIRST_DAY))
                Fields(day, FIRST_DEPARTURE, day, FIRST_RETURN, "", false)
            } else {
                fieldsOf(opened, zone, text(FIRST_DISTANCE))
            }
            val fields = Fields(LocalDate.parse(text(DEPARTURE_DAY)), time(DEPARTURE_TIME),
                LocalDate.parse(text(RETURN_DAY)), time(RETURN_TIME), text(DISTANCE), flag(CAN_CHARGE))
            val draft = Draft(mode, zone, LocalDate.parse(text(TODAY)), first, opened, fields)
            // what the entry itself never puts: a day outside the days offered, or a longer distance
            if (fields.departureDay !in draft.days) {
                unreadable(DEPARTURE_DAY)
            }
            if (fields.returnDay !in draft.days) {
                unreadable(RETURN_DAY)
            }
            if (fields.distance.length > DISTANCE_LENGTH) {
                unreadable(DISTANCE)
            }
            draft
        } catch (_: DateTimeException) {
            // a zone a time-zone update removed, or a value that is no date or time: the entry is closed
            forget(handle)
            null
        } catch (_: IllegalArgumentException) {
            // a value missing, not of its kind or not one the entry puts, such as a distance that is no number or a
            // trip the engine refuses
            forget(handle)
            null
        }
    }

    private fun text(key: String): String = handle.get<Any?>(key) as? String ?: unreadable(key)

    private fun flag(key: String): Boolean = handle.get<Any?>(key) as? Boolean ?: unreadable(key)

    private fun time(key: String): LocalTime =
        LocalTime.ofSecondOfDay((handle.get<Any?>(key) as? Int ?: unreadable(key)).toLong())

    private fun unreadable(key: String): Nothing = throw IllegalArgumentException("$key cannot be read")

    private fun ask(asking: Boolean) {
        if (asking) handle[DISCARD] = true else handle.remove<Boolean>(DISCARD)
        discardNow.value = asking
    }

    private fun close(focusOn: Trip?) {
        forget(handle)
        closedOn = focusOn
        problemNow.value = null
        savingNow.value = SaveState.Idle
        discardNow.value = false
        draftNow.value = null
    }
}
