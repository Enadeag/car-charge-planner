package nl.enadeag.carchargeplanner.android.home

import java.time.LocalDate
import nl.enadeag.carchargeplanner.engine.Outlook
import nl.enadeag.carchargeplanner.engine.config.Percent
import nl.enadeag.carchargeplanner.engine.plan.DayPlan
import nl.enadeag.carchargeplanner.engine.plan.GridAdvice

/**
 * The days of one plan as the dashboard's tabs show them, in the home's time zone, as the desktop's `DayMapper`
 * does; trips are left out.
 *
 * @param planned the plan whose time zone, day of making, assumed battery level and grid advice the days are told in
 */
class DayMapper(planned: Outlook.Planned) {

    private companion object {
        /** Less grid energy than this is shown as none: it is within the forecast's precision. */
        const val NO_GRID_KWH = 0.05
    }

    private val today: LocalDate = planned.madeAt().atZone(planned.zone()).toLocalDate()
    private val assumed = if (planned.batteryAssumed()) planned.batteryLevelPct() else null

    /** The day the grid advice is about; null when the plan gives none. */
    private val gridDay: LocalDate? = planned.gridAdvice().map(GridAdvice::day).orElse(null)

    /**
     * The days, each with its windows and figures.
     *
     * @param days the plan's days, today first; may be empty
     * @return one day model per day, in the same order
     */
    fun map(days: List<DayPlan>): List<DayModel> = days.map(::map)

    private fun map(day: DayPlan): DayModel {
        val main = day.windows().firstOrNull()
        val kind = when {
            main != null -> DayKind.SOLAR
            day.date() == gridDay -> DayKind.GRID
            else -> DayKind.NONE
        }
        return DayModel(
            date = day.date(),
            name = DayName.of(day.date(), today),
            kind = kind,
            main = main?.let {
                MainCharge(it, it.solarKwh() + it.importKwh(), Percent(it.solarShare() * 100),
                    it.importKwh().takeIf { kwh -> kwh >= NO_GRID_KWH })
            },
            later = day.windows().drop(1),
            battery = BatteryLevels(day.startLevelPct(), day.levelAfterChargingPct()),
            assumedLevel = assumed.takeIf { day.date() == today },
        )
    }
}
