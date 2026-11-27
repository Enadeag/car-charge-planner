package nl.enadeag.carchargeplanner.android.home

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import nl.enadeag.carchargeplanner.android.form.NumberText
import nl.enadeag.carchargeplanner.engine.trip.Trip
import nl.enadeag.carchargeplanner.engine.trip.TripEntryRules
import nl.enadeag.carchargeplanner.engine.trip.TripProblem

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
     * Why Save refused what was entered, as the entry shows the engine's [TripProblem], in the order the engine
     * checks.
     *
     * @property field where the owner fixes it
     */
    enum class Problem(val field: TripEntry.Field) {
        /** The return is not after the departure, also when a time the clocks skip moves the departure on. */
        RETURN_NOT_AFTER_DEPARTURE(TripEntry.Field.RETURN),

        /**
         * The return has passed by the time of Save, or the departure is before a trip's earliest moment,
         * [Trip.EARLIEST_INSTANT].
         */
        PASSED(TripEntry.Field.RETURN),

        /**
         * The return is more than a trip may last, [Trip.MAX_LENGTH], after the departure, or in the year 10000 or
         * later.
         */
        TOO_LONG(TripEntry.Field.RETURN),

        /** The distance is 0 or less. */
        DISTANCE_NOT_POSITIVE(TripEntry.Field.DISTANCE),

        /** The distance is no number, or too large to be one. */
        DISTANCE_NOT_A_NUMBER(TripEntry.Field.DISTANCE),

        /** The distance is more than a trip may have, [Trip.MAX_DISTANCE_KM]. */
        DISTANCE_TOO_FAR(TripEntry.Field.DISTANCE),
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
            /** The most characters the distance takes, more than any distance needs. */
            const val DISTANCE_LENGTH = 16

            /**
             * A new trip's fields as the entry opens on them: both days the same, from
             * [TripEntryRules.FIRST_DEPARTURE] to [TripEntryRules.FIRST_RETURN], with no distance and "I can charge
             * at the destination" off.
             *
             * @param day the day the trip leaves and returns
             * @return the fields
             */
            fun new(day: LocalDate) =
                Fields(day, TripEntryRules.FIRST_DEPARTURE, day, TripEntryRules.FIRST_RETURN, "", false)

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
     * The days offered for the departure and the return: [TripEntryRules.DAYS_OFFERED] from today, widened to the
     * days the entry opened on, so a changed trip's own days stay offered.
     */
    val days: ClosedRange<LocalDate>
        get() = minOf(today, first.departureDay)..maxOf(today.plusDays(TripEntryRules.DAYS_OFFERED - 1L),
            first.returnDay)

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
        val typed = fields.distance.trim()
        // a distance left as it first showed, in whole km, stays the trip's own; one that is no number is refused
        // in its place among the problems, after those of the times
        val km = opened?.takeIf { typed == first.distance }?.distanceKm() ?: numbers.read(typed) ?: Double.NaN
        val entered = TripEntryRules.Entered(fields.departureDay.atTime(fields.departureTime),
            fields.returnDay.atTime(fields.returnTime), km, fields.canCharge, opened?.notWithThisCar() == true)
        return when (val checked = TripEntryRules.check(entered, now, zone)) {
            is TripEntryRules.Checked.Valid -> Checked.Valid(checked.trip())
            is TripEntryRules.Checked.Refused -> Checked.Refused(problem(checked.problem()))
        }
    }

    private fun problem(problem: TripProblem): Problem = when (problem) {
        // only a phone whose clock was put back near 1970 offers such a day, and such a trip is over
        TripProblem.DEPARTURE_BEFORE_EARLIEST, TripProblem.PASSED -> Problem.PASSED
        TripProblem.RETURN_NOT_AFTER_DEPARTURE -> Problem.RETURN_NOT_AFTER_DEPARTURE
        TripProblem.TOO_LONG -> Problem.TOO_LONG
        TripProblem.DISTANCE_NOT_POSITIVE -> Problem.DISTANCE_NOT_POSITIVE
        TripProblem.DISTANCE_NOT_A_NUMBER -> Problem.DISTANCE_NOT_A_NUMBER
        TripProblem.DISTANCE_TOO_FAR -> Problem.DISTANCE_TOO_FAR
    }
}
