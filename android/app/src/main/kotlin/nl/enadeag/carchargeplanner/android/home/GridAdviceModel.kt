package nl.enadeag.carchargeplanner.android.home

import java.time.LocalDate
import java.time.ZoneId
import nl.enadeag.carchargeplanner.engine.config.Percent
import nl.enadeag.carchargeplanner.engine.plan.GridAdvice
import nl.enadeag.carchargeplanner.engine.plan.Window

/**
 * Grid advice as today's tab shows it: how much to charge from the grid and when, at what current and charge limit,
 * and why, with the days named as the tabs name them.
 *
 * @property energyKwh how much to charge from the grid, in kWh
 * @property lowDay the day the battery would end below the minimum charge level without it
 * @property window the charge window to take it in; null when no day before [lowDay] has room for it
 * @property windowDay the day of [window]; null without one
 * @property chargeLimit the charge limit to set in the car or the charger; null without a window
 * @property endLevelPct the battery level [lowDay] would end at without the grid charge; 0 or less runs it empty
 * @property minimumPct the minimum charge level it would fall below
 */
data class GridAdviceModel(
    val energyKwh: Double,
    val lowDay: DayName,
    val window: Window?,
    val windowDay: DayName?,
    val chargeLimit: Percent?,
    val endLevelPct: Double,
    val minimumPct: Double,
) {

    /** True when the battery would run empty rather than only end below the minimum. */
    val runsEmpty: Boolean get() = endLevelPct <= 0

    companion object {

        /**
         * The engine's advice, with its days named from the day the plan was made.
         *
         * @param advice the engine's grid advice
         * @param zone the home's time zone, in which the window's day is meant
         * @param today the day the plan was made, in that zone
         * @return the advice as the tab shows it
         */
        fun of(advice: GridAdvice, zone: ZoneId, today: LocalDate): GridAdviceModel {
            val window = advice.window().orElse(null)
            return GridAdviceModel(
                energyKwh = advice.energyKwh(),
                lowDay = DayName.of(advice.day(), today),
                window = window,
                windowDay = window?.let { DayName.of(it.start().atZone(zone).toLocalDate(), today) },
                chargeLimit = advice.chargeLimitPct().orElse(null),
                endLevelPct = advice.endLevelPct(),
                minimumPct = advice.endLevelPct() + advice.deficitPct(),
            )
        }
    }
}
