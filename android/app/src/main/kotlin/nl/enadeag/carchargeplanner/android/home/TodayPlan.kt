package nl.enadeag.carchargeplanner.android.home

import java.time.ZoneId
import nl.enadeag.carchargeplanner.engine.Outlook
import nl.enadeag.carchargeplanner.engine.config.Percent
import nl.enadeag.carchargeplanner.engine.plan.Window

/**
 * What the plain home shows of a plan: today's charge windows in the home's time zone, else the next one, else that
 * there is none today; and the battery level the plan assumed, when it did.
 *
 * @property zone the home's time zone, in which "today" and the times are meant
 * @property windows today's windows, the next one or none
 * @property assumedLevel the battery level the plan starts from when it is assumed rather than entered; null when the
 *     owner entered it
 */
data class TodayPlan(val zone: ZoneId, val windows: Windows, val assumedLevel: Percent?) {

    /** The charge windows the home shows. */
    sealed interface Windows {

        /**
         * Today has charge windows.
         *
         * @property windows today's windows in time order, also those that ended already; never empty
         */
        data class Today(val windows: List<Window>) : Windows

        /**
         * Today has none, but a later day has.
         *
         * @property window the first window that has not ended
         */
        data class Next(val window: Window) : Windows

        /** No charge window today, nor later. */
        data object None : Windows
    }

    companion object {

        /**
         * What the home shows of a plan.
         *
         * @param planned the engine's plan; its first day is today in the home's time zone
         * @return today's windows, the next one or none, and the battery level when assumed
         */
        fun of(planned: Outlook.Planned): TodayPlan {
            val today = planned.madeAt().atZone(planned.zone()).toLocalDate()
            val todays = planned.days().firstOrNull { it.date() == today }?.windows().orEmpty()
            val windows = when {
                todays.isNotEmpty() -> Windows.Today(todays)
                else -> planned.nextWindow().map<Windows> { Windows.Next(it) }.orElse(Windows.None)
            }
            return TodayPlan(planned.zone(), windows, if (planned.batteryAssumed()) planned.batteryLevelPct() else null)
        }
    }
}
