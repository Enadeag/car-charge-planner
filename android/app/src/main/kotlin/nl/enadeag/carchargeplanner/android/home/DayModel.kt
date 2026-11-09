package nl.enadeag.carchargeplanner.android.home

import java.time.LocalDate
import nl.enadeag.carchargeplanner.engine.config.Percent
import nl.enadeag.carchargeplanner.engine.dashboard.DayChart
import nl.enadeag.carchargeplanner.engine.dashboard.DayName
import nl.enadeag.carchargeplanner.engine.plan.Window

/**
 * One day of the plan as the dashboard shows it, in the home's time zone: its charge windows, what to set for the
 * first, and the figures by source. The screen writes the words; what is shown and what is left out is decided here.
 *
 * @property date the day, in the home's time zone
 * @property name how the day is named: today, tomorrow or by its date
 * @property kind where the day's charge comes from, which also says why a day without a charge window has none
 * @property main the day's first charge window with its figures, the one the day is about; null when it has none
 * @property later the day's other charge windows, in time order; empty when it has one or none
 * @property battery the battery level at the start of the day and after its charging
 * @property assumedLevel the battery level the plan assumed, as the day's note says; only today has it, and only
 *     while the owner entered no level
 * @property chart the day's expected solar power, the house's use and the charge windows, as the chart draws them
 * @throws IllegalArgumentException when the day is [DayKind.SOLAR] without a main window, or has one while it is
 *     another kind, or has later windows without a main one
 */
data class DayModel(
    val date: LocalDate,
    val name: DayName,
    val kind: DayKind,
    val main: MainCharge?,
    val later: List<Window>,
    val battery: BatteryLevels,
    val assumedLevel: Percent?,
    val chart: DayChart,
) {

    init {
        if ((kind == DayKind.SOLAR) != (main != null)) {
            throw IllegalArgumentException("a day has a main window exactly when it is SOLAR, not $kind with $main")
        }
        if (main == null && later.isNotEmpty()) {
            throw IllegalArgumentException("a day without a main window has no later ones, not $later")
        }
    }

    /** True on the day a trip the plan advises on leaves, whose advice says what the battery needs. */
    val departs: Boolean get() = battery.afterTripPct != null

    /** The day's figures by source, each shown in its source's colours: the solar share, the battery, the grid. */
    val figures: List<SourceFigure>
        get() = listOfNotNull(main?.let { SourceFigure.SolarShare(it.solarShare) }, SourceFigure.Battery(battery),
            main?.gridKwh?.let { SourceFigure.FromGrid(it) })
}

/** An energy source the dashboard shows figures of, each in colours of its own. */
enum class EnergySource {

    /** The solar panels. */
    SOLAR,

    /** The grid. */
    GRID,

    /** The car and its battery. */
    CAR,
}

/** A figure about one energy source, as a day shows it. */
sealed interface SourceFigure {

    /** The source the figure is about, whose colours it is drawn in. */
    val source: EnergySource

    /**
     * How much of the main window's charge is solar.
     *
     * @property share the solar share
     */
    data class SolarShare(val share: Percent) : SourceFigure {
        override val source: EnergySource get() = EnergySource.SOLAR
    }

    /**
     * The battery level at the start of the day and after its charging.
     *
     * @property levels the levels
     */
    data class Battery(val levels: BatteryLevels) : SourceFigure {
        override val source: EnergySource get() = EnergySource.CAR
    }

    /**
     * What the main window takes from the grid.
     *
     * @property kwh the energy, in kWh
     */
    data class FromGrid(val kwh: Double) : SourceFigure {
        override val source: EnergySource get() = EnergySource.GRID
    }
}

/**
 * A day's main charge window and what it charges.
 *
 * @property window the charge window
 * @property energyKwh about how much it charges, in kWh; finite, 0 or more
 * @property solarShare how much of that is solar
 * @property gridKwh what it takes from the grid, in kWh, finite and more than 0; null when it is too little to show
 * @throws IllegalArgumentException when [energyKwh] is negative, infinite or not a number, or [gridKwh] is not null
 *     and not finite and more than 0
 */
data class MainCharge(val window: Window, val energyKwh: Double, val solarShare: Percent, val gridKwh: Double?) {

    init {
        if (!(energyKwh >= 0) || energyKwh.isInfinite()) {
            throw IllegalArgumentException("the energy must be finite and 0 or more, not $energyKwh kWh")
        }
        if (gridKwh != null && (!(gridKwh > 0) || gridKwh.isInfinite())) {
            throw IllegalArgumentException("the grid energy must be finite and more than 0, not $gridKwh kWh")
        }
    }
}

/**
 * The battery level at the start of a day and after its charging, and on the day a trip the plan advises on leaves,
 * at the end of the day, after the trip.
 *
 * @property startPct at the start of the day; today's at the moment the plan was made
 * @property afterPct after the day's charging; the start's when the day has none
 * @property afterTripPct at the end of the day a trip leaves, after the trip; null on any other day
 */
data class BatteryLevels(val startPct: Percent, val afterPct: Percent, val afterTripPct: Percent? = null)

/**
 * The minimum and target charge levels of the plan, which every day's battery bar marks.
 *
 * @property minimum the minimum charge level, not above [target]
 * @property target the target charge level
 * @throws IllegalArgumentException when the minimum is above the target
 */
data class ChargeLevels(val minimum: Percent, val target: Percent) {

    init {
        if (minimum.value() > target.value()) {
            throw IllegalArgumentException("the minimum charge level must not be above the target, not " +
                "${minimum.value()}% over ${target.value()}%")
        }
    }
}

/** Where a day's charge comes from. */
enum class DayKind {

    /** The day has a charge window. */
    SOLAR,

    /** The day without a charge window that would end below the minimum charge level, as the grid advice says. */
    GRID,

    /** The day has no charge window: the car needs none, or there is too little solar power. */
    NONE,
}

/** Why a day without a charge window has none, as its note says. */
enum class NoWindowWhy {

    /** Solar power is not enough to stay above the minimum charge level: the grid advice says what to do. */
    GRID,

    /** The car is away on a trip, or there is too little solar power while it is home. */
    AWAY,

    /** The car needs no charging, or there is too little solar power for it. */
    NOT_NEEDED,
}
