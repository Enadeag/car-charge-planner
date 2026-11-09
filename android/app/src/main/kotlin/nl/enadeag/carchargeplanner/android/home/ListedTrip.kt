package nl.enadeag.carchargeplanner.android.home

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import nl.enadeag.carchargeplanner.engine.dashboard.DayName
import nl.enadeag.carchargeplanner.engine.trip.Trip

/**
 * A stored trip as the dashboard lists it, in the home's time zone, as the desktop's `TripMapper` does: the trip
 * itself, so a choice or Remove can hand it to the engine, the days whose pages list it, and how its departure and
 * return days are named.
 *
 * @property trip the trip as the engine stores it
 * @property firstDay the day of its departure
 * @property lastDay the last day it takes: the day of its return, or the day before when it returns at midnight
 * @property departureDay the day of its departure, named from the day the plan was made
 * @property returnDay the day of its return, named from the day the plan was made; the departure's when it returns
 *     the day it leaves
 * @property sameDay true when it returns on the day it leaves, so its times are told without their days; a trip back
 *     at midnight returns on the next day
 * @throws IllegalArgumentException when [lastDay] is before [firstDay]
 */
data class ListedTrip(val trip: Trip, val firstDay: LocalDate, val lastDay: LocalDate, val departureDay: DayName,
    val returnDay: DayName, val sameDay: Boolean) {

    init {
        if (lastDay.isBefore(firstDay)) {
            throw IllegalArgumentException("a trip's last day is not before its first, not $lastDay before $firstDay")
        }
    }

    /** When it leaves. */
    val departure: Instant get() = trip.departure()

    /** When it is back. */
    val returnTime: Instant get() = trip.returnTime()

    /**
     * Whether the car is away for this trip on a day, so that day's page lists it.
     *
     * @param day a day in the home's time zone
     * @return true from the day of its departure to its last day, both included
     */
    fun covers(day: LocalDate): Boolean = !day.isBefore(firstDay) && !day.isAfter(lastDay)

    companion object {

        /**
         * The trips as the dashboard lists them: all of them, also one under way or one that returned in the last day,
         * as the engine keeps them.
         *
         * @param trips the stored trips, in order of their departure; may be empty
         * @param zone the home's time zone, in which the days are told
         * @param madeAt when the plan was made, whose day is today
         * @return one per trip, in the same order
         */
        fun of(trips: List<Trip>, zone: ZoneId, madeAt: Instant): List<ListedTrip> =
            trips.map { of(it, zone, madeAt) }

        /**
         * One trip as the dashboard tells it.
         *
         * @param trip the trip
         * @param zone the home's time zone, in which the days are told
         * @param madeAt when the plan was made, whose day is today
         * @return the trip with its days
         */
        fun of(trip: Trip, zone: ZoneId, madeAt: Instant): ListedTrip {
            val today = madeAt.atZone(zone).toLocalDate()
            val first = trip.firstDay(zone)
            val back = trip.returnTime().atZone(zone).toLocalDate()
            return ListedTrip(trip, first, trip.lastDay(zone), DayName.of(first, today), DayName.of(back, today),
                first == back)
        }
    }
}
