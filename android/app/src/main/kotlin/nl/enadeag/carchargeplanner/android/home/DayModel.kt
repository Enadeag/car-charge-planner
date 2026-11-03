package nl.enadeag.carchargeplanner.android.home

import java.time.LocalDate
import nl.enadeag.carchargeplanner.engine.config.Percent
import nl.enadeag.carchargeplanner.engine.plan.Window

/**
 * One day of the plan as the dashboard shows it, in the home's time zone: its charge windows, what to set for the
 * first, and the figures by source. The screen writes the words; what is shown and what is left out is decided here.
 *
 * @property date the day, in the home's time zone
 * @property name how the day is named: today, tomorrow or by its date
 * @property kind where the day's charge comes from, which also says why a day without a charge window has none
 * @property main the day's first charge window, the one the day is about; null when it has none
 * @property later the day's other charge windows, in time order; empty when it has one or none
 * @property energyKwh about how much the main window charges, in kWh; null without one
 * @property solarSharePct the main window's solar share, from 0 to 100; null without one
 * @property gridKwh what the main window takes from the grid, in kWh; null without one, or when it is too little to
 *     show
 * @property battery the battery level at the start of the day and after its charging
 * @property assumedLevel the battery level the plan assumed, as the day's note says; only today has it, and only
 *     while the owner entered no level
 */
data class DayModel(
    val date: LocalDate,
    val name: DayName,
    val kind: DayKind,
    val main: Window?,
    val later: List<Window>,
    val energyKwh: Double?,
    val solarSharePct: Double?,
    val gridKwh: Double?,
    val battery: BatteryLevels,
    val assumedLevel: Percent?,
)

/**
 * The battery level at the start of a day and after its charging.
 *
 * @property startPct at the start of the day; today's at the moment the plan was made
 * @property afterPct after the day's charging; the start's when the day has none
 */
data class BatteryLevels(val startPct: Percent, val afterPct: Percent)

/** Where a day's charge comes from. */
enum class DayKind {

    /** The day has a charge window. */
    SOLAR,

    /** The day without a charge window that would end below the minimum charge level, as the grid advice says. */
    GRID,

    /** The day has no charge window: the car needs none, or there is too little solar power. */
    NONE,
}

/** How a day is named, from the day the plan was made in the home's time zone. */
sealed interface DayName {

    /** The day the plan was made. */
    data object Today : DayName

    /** The day after. */
    data object Tomorrow : DayName

    /**
     * Any other day, named by its weekday or date.
     *
     * @property date the day
     */
    data class Other(val date: LocalDate) : DayName

    companion object {

        /**
         * The name of a day.
         *
         * @param date the day to name
         * @param today the day the plan was made, in the home's time zone
         * @return today, tomorrow, or the other day
         */
        fun of(date: LocalDate, today: LocalDate): DayName = when (date) {
            today -> Today
            today.plusDays(1) -> Tomorrow
            else -> Other(date)
        }
    }
}
