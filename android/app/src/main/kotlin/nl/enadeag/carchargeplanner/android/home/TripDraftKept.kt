package nl.enadeag.carchargeplanner.android.home

import androidx.lifecycle.SavedStateHandle
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import nl.enadeag.carchargeplanner.android.form.forget
import nl.enadeag.carchargeplanner.engine.trip.Trip

/**
 * The trip entry's draft in the saved state, so it survives turning the phone and the system ending the app: how the
 * entry opened, put once as it opens, and the fields, put at each change. A draft that can no longer be read is
 * forgotten. Called from the main thread.
 *
 * @param handle the screen's saved state
 */
internal class TripDraftKept(private val handle: SavedStateHandle) {

    companion object {
        /** What every key of the trip entry starts with. */
        const val PREFIX = "tripEntry"

        /** The key of "Discard changes?". */
        const val DISCARD = "$PREFIX.discard"

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

        /**
         * Forgets everything the trip entry kept in the handle.
         *
         * @param handle the screen's saved state
         */
        fun forget(handle: SavedStateHandle) = handle.forget(listOf(PREFIX))
    }

    /**
     * Puts how the entry opened, and its fields as they are.
     *
     * @param draft the draft as the entry opens
     */
    fun opened(draft: TripDraft) {
        handle[MODE] = draft.mode.name
        handle[ZONE] = draft.zone.id
        handle[TODAY] = draft.today.toString()
        val trip = draft.opened
        if (trip == null) {
            handle[FIRST_DAY] = draft.first.departureDay.toString()
        } else {
            handle[OPENED_DEPARTURE] = trip.departure().toString()
            handle[OPENED_RETURN] = trip.returnTime().toString()
            handle[OPENED_KM] = trip.distanceKm().toString()
            handle[OPENED_CAN_CHARGE] = trip.canChargeAtDestination()
            handle[OPENED_NOT_WITH] = trip.notWithThisCar()
            handle[FIRST_DISTANCE] = draft.first.distance
        }
        put(draft.fields)
    }

    /**
     * Puts the fields as they are now.
     *
     * @param fields the fields
     */
    fun put(fields: TripDraft.Fields) {
        handle[DEPARTURE_DAY] = fields.departureDay.toString()
        handle[DEPARTURE_TIME] = fields.departureTime.toSecondOfDay()
        handle[RETURN_DAY] = fields.returnDay.toString()
        handle[RETURN_TIME] = fields.returnTime.toSecondOfDay()
        handle[DISTANCE] = fields.distance
        handle[CAN_CHARGE] = fields.canCharge
    }

    /**
     * The draft the handle keeps; one that can no longer be read is forgotten.
     *
     * @return the draft; null when none is kept, or it was forgotten
     */
    fun kept(): TripDraft? {
        if (handle.get<Any?>(MODE) == null) {
            return null
        }
        return try {
            val mode = TripDraft.Mode.valueOf(text(MODE))
            val zone = ZoneId.of(text(ZONE))
            val opened = if (mode == TripDraft.Mode.CHANGE) Trip(
                Instant.parse(text(OPENED_DEPARTURE)), Instant.parse(text(OPENED_RETURN)), text(OPENED_KM).toDouble(),
                flag(OPENED_CAN_CHARGE), flag(OPENED_NOT_WITH)) else null
            val first = if (opened == null) {
                TripDraft.Fields.new(LocalDate.parse(text(FIRST_DAY)))
            } else {
                TripDraft.Fields.of(opened, zone, text(FIRST_DISTANCE))
            }
            val fields = TripDraft.Fields(LocalDate.parse(text(DEPARTURE_DAY)), time(DEPARTURE_TIME),
                LocalDate.parse(text(RETURN_DAY)), time(RETURN_TIME), text(DISTANCE), flag(CAN_CHARGE))
            val draft = TripDraft(mode, zone, LocalDate.parse(text(TODAY)), first, opened, fields)
            // what the entry itself never puts: a day outside the days offered, or a longer distance
            if (fields.departureDay !in draft.days) {
                unreadable(DEPARTURE_DAY)
            }
            if (fields.returnDay !in draft.days) {
                unreadable(RETURN_DAY)
            }
            if (fields.distance.length > TripDraft.Fields.DISTANCE_LENGTH) {
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
}
