package nl.enadeag.carchargeplanner.android.home

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import nl.enadeag.carchargeplanner.android.form.NumberText
import nl.enadeag.carchargeplanner.engine.trip.Trip

/**
 * What the open trip entry ([TripEntry]) holds.
 *
 * @property mode a new trip or a change
 * @property zone the home's time zone, in which the days and times are read
 * @property today the home's today by the clock when the entry opened, which names the days and starts those
 *     offered
 * @property first the fields as the entry opened, its distance as it first showed
 * @property opened the trip as the entry opened it, exactly as stored; null for a new trip
 * @property fields the fields as they are now
 */
data class TripDraft(val mode: Mode, val zone: ZoneId, val today: LocalDate, val first: Fields, val opened: Trip?,
    val fields: Fields) {

    /** Whether the entry enters a new trip or changes one. */
    enum class Mode {
        /** A new trip, from Add. */
        ADD,

        /** A change to a stored trip, from its Change. */
        CHANGE,
    }

    /**
     * Why Save refused what was entered, in the order the entry checks.
     *
     * @property field where the owner fixes it
     */
    enum class Problem(val field: TripEntry.Field) {
        /** The return is not after the departure, also when a time the clocks skip moves the departure on. */
        RETURN_NOT_AFTER_DEPARTURE(TripEntry.Field.RETURN),

        /** The return has passed by the time of Save. */
        PASSED(TripEntry.Field.RETURN),

        /** The distance is no number, or too large to be one. */
        DISTANCE_NOT_A_NUMBER(TripEntry.Field.DISTANCE),

        /** The distance is 0 or less. */
        DISTANCE_NOT_POSITIVE(TripEntry.Field.DISTANCE),
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
        val returnTime: LocalTime, val distance: String, val canCharge: Boolean) {

        companion object {
            /**
             * A stored trip's fields as the entry opens on them.
             *
             * @param trip the trip
             * @param zone the home's time zone, in which the days and times are read
             * @param distance the trip's distance as written at the open
             * @return the fields, the times to the minute
             */
            fun of(trip: Trip, zone: ZoneId, distance: String): Fields {
                val leaves = trip.departure().atZone(zone)
                val back = trip.returnTime().atZone(zone)
                return Fields(leaves.toLocalDate(), leaves.toLocalTime().truncatedTo(ChronoUnit.MINUTES),
                    back.toLocalDate(), back.toLocalTime().truncatedTo(ChronoUnit.MINUTES), distance,
                    trip.canChargeAtDestination())
            }
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
     * The days offered for the departure and the return, as the desktop's: from today to 20 days after it, widened
     * to the days the entry opened on, so a changed trip's own days stay offered.
     */
    val days: ClosedRange<LocalDate>
        get() = minOf(today, first.departureDay)..maxOf(today.plusDays(TripEntry.DAYS_OFFERED - 1L), first.returnDay)

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
