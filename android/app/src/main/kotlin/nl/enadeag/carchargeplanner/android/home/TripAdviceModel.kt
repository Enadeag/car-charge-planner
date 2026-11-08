package nl.enadeag.carchargeplanner.android.home

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.ceil
import nl.enadeag.carchargeplanner.engine.Outlook
import nl.enadeag.carchargeplanner.engine.config.Percent
import nl.enadeag.carchargeplanner.engine.plan.TripAdvice
import nl.enadeag.carchargeplanner.engine.plan.TripGridCharge
import nl.enadeag.carchargeplanner.engine.plan.Window

/**
 * The engine's advice for one trip as the dashboard shows it, as the desktop's `TripAdviceMapper` decides it: whether
 * the plan charges enough already or what to charge and when, where the charge comes from, the charge limit, how far
 * the battery falls short, and the battery level the advice starts from. The days are real dates in the home's time
 * zone, so the screen names them as the tab it shows on does; the words are the screen's. It knows nothing of Android,
 * so the Android and desktop apps can share it (#341).
 *
 * @property departure when the trip leaves
 * @property departureDay the day of [departure], in the home's time zone
 * @property distanceKm the trip's distance there and back, in km
 * @property plan what the plan does for the trip
 * @property source where the trip's charge comes from: the grid when it needs a grid charge or the battery falls short,
 *     solar when solar power above the target charge level is enough, the car when the plan charges enough already
 * @property shortKm how many km the battery falls short for the trip, rounded up to a whole km; null when it does not
 * @property level the battery level the plan starts from
 * @property levelAssumed true when that level is assumed, false when the owner entered it
 */
data class TripAdviceModel(
    val departure: Instant,
    val departureDay: LocalDate,
    val distanceKm: Double,
    val plan: Plan,
    val source: EnergySource,
    val shortKm: Int?,
    val level: Percent,
    val levelAssumed: Boolean,
) {

    /**
     * True when the owner has to do something for the trip: charge above the target charge level, charge from the
     * grid, or plan a charging stop.
     */
    val asksAction: Boolean get() = plan !is Plan.Enough

    /**
     * Whether the advice belongs on a day's tab: always on the day of the departure, and on today's when the owner has
     * to do something.
     *
     * @param day the day of the tab
     * @param today the day the plan was made, in the home's time zone
     * @return true when the tab of the day shows the advice
     */
    fun shownOn(day: LocalDate, today: LocalDate): Boolean = day == departureDay || asksAction && day == today

    /** What the plan does for a trip. */
    sealed interface Plan {

        /**
         * The plan charges enough for it already.
         *
         * @property departurePct the battery level when the trip leaves
         */
        data class Enough(val departurePct: Percent) : Plan

        /**
         * Charge to the limit on solar power, in windows above the target charge level.
         *
         * @property limit the level to charge to, which is also the charge limit
         * @property windows the solar windows, by day
         */
        data class Solar(val limit: Percent, val windows: List<DayWindows>) : Plan

        /**
         * Charge on solar power, then from the grid up to the limit.
         *
         * @property limit the level to charge to, which is also the charge limit to set
         * @property windows the solar windows, by day
         * @property grid the grid charge
         */
        data class SolarThenGrid(val limit: Percent, val windows: List<DayWindows>, val grid: GridStart) : Plan

        /**
         * Charge from the grid alone, up to the limit.
         *
         * @property limit the level to charge to, which is also the charge limit to set
         * @property grid the grid charge
         */
        data class Grid(val limit: Percent, val grid: GridStart) : Plan

        /**
         * No charge before the trip helps: only the level it leaves with is told, beside how far it falls short.
         *
         * @property departurePct the battery level when the trip leaves
         */
        data class Leaves(val departurePct: Percent) : Plan
    }

    /**
     * The solar windows of one day.
     *
     * @property day the day, in the home's time zone
     * @property windows its windows, in time order; never empty
     */
    data class DayWindows(val day: LocalDate, val windows: List<Window>)

    /**
     * The grid charge for a trip and when to start it.
     *
     * @property window the grid charge's window, whose current and power to set
     * @property start why it starts when it does: as planned, now with little time left, or now outside the charging
     *     hours
     * @property now true when it starts no later than the moment the plan was made, so it is "now" rather than a time
     */
    data class GridStart(val window: Window, val start: TripGridCharge.Start, val now: Boolean)

    companion object {

        /**
         * The advice for each trip of a plan.
         *
         * @param planned the plan whose trip advice, time zone, moment of making, target charge level and battery level
         *     the advice is told in
         * @return one per trip, in the engine's order, by departure
         */
        fun of(planned: Outlook.Planned): List<TripAdviceModel> = planned.tripAdvice().map {
            of(it, planned.zone(), planned.madeAt(), planned.targetPct(), planned.batteryLevelPct(),
                planned.batteryAssumed())
        }

        /**
         * The advice for one trip.
         *
         * @param advice the engine's advice
         * @param zone the home's time zone
         * @param madeAt when the plan was made, up to which a grid start is "now"
         * @param target the target charge level, up to which the plan charges anyway
         * @param level the battery level the plan starts from
         * @param assumed true when that level is assumed
         * @return the advice as the dashboard shows it
         */
        fun of(advice: TripAdvice, zone: ZoneId, madeAt: Instant, target: Percent, level: Percent,
            assumed: Boolean): TripAdviceModel {
            val trip = advice.trip()
            val grid = advice.grid().orElse(null)
            val short = advice.kmShort() > 0
            val enough = grid == null && !short &&
                (advice.solarWindows().isEmpty() || advice.neededPct().value() <= target.value())
            val source = when {
                enough -> EnergySource.CAR
                grid == null && !short -> EnergySource.SOLAR
                else -> EnergySource.GRID
            }
            return TripAdviceModel(trip.departure(), trip.firstDay(zone), trip.distanceKm(),
                if (enough) Plan.Enough(advice.departureLevelPct()) else plan(advice, grid, zone, madeAt), source,
                if (short) ceil(advice.kmShort()).toInt() else null, level, assumed)
        }

        private fun plan(advice: TripAdvice, grid: TripGridCharge?, zone: ZoneId, madeAt: Instant): Plan {
            val limit = advice.chargeLimitPct()
            val solar = byDay(advice.solarWindows(), zone)
            val start = grid?.let { GridStart(it.window(), it.start(), !it.window().start().isAfter(madeAt)) }
            return when {
                start != null && solar.isEmpty() -> Plan.Grid(limit, start)
                start != null -> Plan.SolarThenGrid(limit, solar, start)
                solar.isNotEmpty() -> Plan.Solar(limit, solar)
                else -> Plan.Leaves(advice.departureLevelPct())
            }
        }

        private fun byDay(windows: List<Window>, zone: ZoneId): List<DayWindows> =
            windows.groupBy { it.start().atZone(zone).toLocalDate() }.map { (day, inDay) -> DayWindows(day, inDay) }
    }
}
