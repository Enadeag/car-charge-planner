package nl.enadeag.carchargeplanner.android.home

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import nl.enadeag.carchargeplanner.engine.away.OneOffAwayPeriod

/**
 * A one-off away period that has not ended, as the dashboard lists it, in the home's time zone, with the stored period
 * itself, so Remove can hand it to the engine.
 */
sealed interface ComingAway {

    /** The period as the engine stores it. */
    val period: OneOffAwayPeriod

    /**
     * The car is away the whole day.
     *
     * @property day the day
     * @property period the period as the engine stores it, from the day's midnight to the next
     */
    data class AllDay(val day: DayName, override val period: OneOffAwayPeriod) : ComingAway

    /**
     * The car is away part of one day.
     *
     * @property day the day
     * @property period the period as the engine stores it, within the day
     */
    data class PartOfDay(val day: DayName, override val period: OneOffAwayPeriod) : ComingAway {

        /** When it leaves. */
        val start: Instant get() = period.start()

        /** When it is back, the same day. */
        val end: Instant get() = period.end()
    }

    /**
     * The car is away from one day to a later one.
     *
     * @property startDay the day it leaves
     * @property endDay the day it is back
     * @property period the period as the engine stores it
     */
    data class Days(val startDay: DayName, val endDay: DayName, override val period: OneOffAwayPeriod) : ComingAway {

        /** When it leaves. */
        val start: Instant get() = period.start()

        /** When it is back. */
        val end: Instant get() = period.end()
    }

    companion object {

        /**
         * The periods that have not ended when the plan was made, as the desktop's `OneOffAwayMapper` lists them.
         *
         * @param periods the stored one-off away periods, in order of their start; may be empty
         * @param zone the home's time zone, in which the days and times are told
         * @param madeAt when the plan was made; periods that ended by then are left out
         * @return those that end after [madeAt], in the same order
         */
        fun of(periods: List<OneOffAwayPeriod>, zone: ZoneId, madeAt: Instant): List<ComingAway> =
            periods.filter { it.end().isAfter(madeAt) }.map { describe(it, zone, madeAt) }

        /**
         * One period as the dashboard tells it, whether or not it ended, such as for the snackbar after a change.
         *
         * @param period the period
         * @param zone the home's time zone, in which the days and times are told
         * @param madeAt when the plan was made, whose day is today
         * @return the period by kind, its days named from that day
         */
        fun describe(period: OneOffAwayPeriod, zone: ZoneId, madeAt: Instant): ComingAway {
            val today: LocalDate = madeAt.atZone(zone).toLocalDate()
            val startDay = period.start().atZone(zone).toLocalDate()
            val endDay = period.end().atZone(zone).toLocalDate()
            return when {
                period == OneOffAwayPeriod.allDay(startDay, zone) -> AllDay(DayName.of(startDay, today), period)
                startDay == endDay -> PartOfDay(DayName.of(startDay, today), period)
                else -> Days(DayName.of(startDay, today), DayName.of(endDay, today), period)
            }
        }
    }
}
