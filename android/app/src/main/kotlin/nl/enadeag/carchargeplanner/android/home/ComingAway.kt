package nl.enadeag.carchargeplanner.android.home

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import nl.enadeag.carchargeplanner.engine.away.OneOffAwayPeriod

/** A one-off away period that has not ended, as the dashboard lists it, in the home's time zone. */
sealed interface ComingAway {

    /**
     * The car is away the whole day.
     *
     * @property day the day
     */
    data class AllDay(val day: DayName) : ComingAway

    /**
     * The car is away part of one day.
     *
     * @property day the day
     * @property start when it leaves
     * @property end when it is back, the same day
     */
    data class PartOfDay(val day: DayName, val start: Instant, val end: Instant) : ComingAway

    /**
     * The car is away from one day to a later one.
     *
     * @property startDay the day it leaves
     * @property start when it leaves
     * @property endDay the day it is back
     * @property end when it is back
     */
    data class Days(val startDay: DayName, val start: Instant, val endDay: DayName, val end: Instant) : ComingAway

    companion object {

        /**
         * The periods that have not ended when the plan was made, as the desktop's `OneOffAwayMapper` lists them.
         *
         * @param periods the stored one-off away periods, in order of their start; may be empty
         * @param zone the home's time zone, in which the days and times are told
         * @param madeAt when the plan was made; periods that ended by then are left out
         * @return those that end after [madeAt], in the same order
         */
        fun of(periods: List<OneOffAwayPeriod>, zone: ZoneId, madeAt: Instant): List<ComingAway> {
            val today = madeAt.atZone(zone).toLocalDate()
            return periods.filter { it.end().isAfter(madeAt) }.map { describe(it, zone, today) }
        }

        private fun describe(period: OneOffAwayPeriod, zone: ZoneId, today: LocalDate): ComingAway {
            val startDay = period.start().atZone(zone).toLocalDate()
            val endDay = period.end().atZone(zone).toLocalDate()
            return when {
                period == OneOffAwayPeriod.allDay(startDay, zone) -> AllDay(DayName.of(startDay, today))
                startDay == endDay -> PartOfDay(DayName.of(startDay, today), period.start(), period.end())
                else -> Days(DayName.of(startDay, today), period.start(), DayName.of(endDay, today), period.end())
            }
        }
    }
}
