package nl.enadeag.carchargeplanner.android.home

import java.time.LocalDate
import nl.enadeag.carchargeplanner.engine.Outlook
import nl.enadeag.carchargeplanner.engine.dashboard.DashboardDay
import nl.enadeag.carchargeplanner.engine.plan.DayPlan
import nl.enadeag.carchargeplanner.engine.plan.GridAdvice

/**
 * The days of one plan as the dashboard's tabs show them, in the home's time zone, as the desktop's `DayMapper`
 * does; which days the car is away on a trip is the dashboard's, as the trips change without a new plan.
 *
 * @param planned the plan whose time zone, day of making, assumed battery level, grid advice, trip advice and days
 *     the days are told in
 */
class DayMapper(planned: Outlook.Planned) {

    private companion object {
        /** Less grid energy than this is shown as none: it is within the forecast's precision. */
        const val NO_GRID_KWH = 0.05
    }

    private val today: LocalDate = planned.today()
    private val assumed = if (planned.batteryAssumed()) planned.batteryLevelPct() else null

    /** The plan's days, by their date. */
    private val plans: Map<LocalDate, DayPlan> = planned.days().associateBy(DayPlan::date)

    /** The day the grid advice is about; null when the plan gives none. */
    private val gridDay: LocalDate? = planned.gridAdvice().map(GridAdvice::day).orElse(null)

    /** The days a trip the plan advises on leaves. */
    private val departures: Set<LocalDate> =
        planned.tripAdvice().map { it.trip().firstDay(planned.zone()) }.toSet()

    /**
     * The days, each with its windows and figures.
     *
     * @param days the engine's dashboard days of the plan, today first, with their names and charts; may be empty
     * @return one day model per day, in the same order
     * @throws IllegalArgumentException when a day is not one of the plan's
     */
    fun map(days: List<DashboardDay>): List<DayModel> = days.map(::map)

    private fun map(shown: DashboardDay): DayModel {
        val date = shown.name().date()
        val day = plans[date] ?: throw IllegalArgumentException("the plan has no day $date")
        val main = day.windows().firstOrNull()
        val kind = when {
            main != null -> DayKind.SOLAR
            day.date() == gridDay -> DayKind.GRID
            else -> DayKind.NONE
        }
        return DayModel(
            date = day.date(),
            name = shown.name(),
            kind = kind,
            main = main?.let {
                MainCharge(it, it.energyKwh(), it.solarSharePct(),
                    it.importKwh().takeIf { kwh -> kwh >= NO_GRID_KWH })
            },
            later = day.windows().drop(1),
            battery = BatteryLevels(day.startLevelPct(), day.levelAfterChargingPct(),
                day.endLevelPct().takeIf { day.date() in departures }),
            assumedLevel = assumed.takeIf { day.date() == today },
            chart = shown.chart(),
        )
    }
}
