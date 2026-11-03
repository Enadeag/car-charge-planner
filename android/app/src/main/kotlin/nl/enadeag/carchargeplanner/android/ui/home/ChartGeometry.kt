package nl.enadeag.carchargeplanner.android.ui.home

import kotlin.math.max
import nl.enadeag.carchargeplanner.android.home.BatteryLevels
import nl.enadeag.carchargeplanner.android.home.ChargeLevels
import nl.enadeag.carchargeplanner.android.home.SolarChart

/**
 * Where the solar chart's plot lies in the chart, and the hours and power it spans: turns hours and kW into pixels.
 *
 * @property left the plot's left edge in pixels from the chart's left
 * @property top the plot's top edge in pixels from the chart's top
 * @property width the plot's width in pixels, 0 or more
 * @property height the plot's height in pixels, 0 or more
 * @property fromHour the hour at the left edge
 * @property toHour the hour at the right edge, after [fromHour]
 * @property scaleKw the power at the top edge in kW, more than 0
 * @throws IllegalArgumentException when a size is negative, the hours do not run forwards or the scale is not more
 *     than 0
 */
internal data class PlotArea(val left: Float, val top: Float, val width: Float, val height: Float,
    val fromHour: Double, val toHour: Double, val scaleKw: Double) {

    init {
        if (!(width >= 0 && height >= 0)) {
            throw IllegalArgumentException("a plot cannot have a negative size, not $width by $height")
        }
        if (!(toHour > fromHour)) {
            throw IllegalArgumentException("a plot's hours must run forwards, not $fromHour to $toHour")
        }
        if (!(scaleKw > 0)) {
            throw IllegalArgumentException("a plot's scale must be more than 0 kW, not $scaleKw")
        }
    }

    /** The bottom edge, where the power is 0, in pixels from the chart's top. */
    val bottom: Float get() = top + height

    /** The right edge, in pixels from the chart's left. */
    val right: Float get() = left + width

    /**
     * Where an hour is.
     *
     * @param hour the hour from the start of the day; one outside the plot's hours goes on its nearest edge
     * @return pixels from the chart's left
     */
    fun x(hour: Double): Float = left + width * ((hour.coerceIn(fromHour, toHour) - fromHour) / (toHour - fromHour))
        .toFloat()

    /**
     * Where a power is.
     *
     * @param kw the power in kW; below 0 goes on the bottom edge and above the scale on the top edge
     * @return pixels from the chart's top
     */
    fun y(kw: Double): Float = bottom - height * (kw.coerceIn(0.0, scaleKw) / scaleKw).toFloat()
}

/**
 * A point of the solar curve.
 *
 * @property hour the hour from the start of the day
 * @property kw the power in kW
 */
internal data class CurvePoint(val hour: Double, val kw: Double)

/**
 * The solar curve's outline in hours and kW: it passes through the middle of each step at its power, and starts and
 * ends at 0 so it closes into an area.
 *
 * @param steps the forecast steps in time order; may be empty
 * @param fromHour the first hour shown; the outline does not start before it
 * @param toHour the last hour shown, after [fromHour]; the outline does not end after it
 * @return 0 at the first step's start, each step's middle, 0 at the last step's end; without steps a flat line at 0
 *     over the hours shown
 */
internal fun curveOf(steps: List<SolarChart.Step>, fromHour: Double, toHour: Double): List<CurvePoint> {
    if (steps.isEmpty()) {
        return listOf(CurvePoint(fromHour, 0.0), CurvePoint(toHour, 0.0))
    }
    return listOf(CurvePoint(max(fromHour, steps.first().startHour), 0.0)) +
        steps.map { CurvePoint(it.midHour.coerceIn(fromHour, toHour), it.kw) } +
        CurvePoint(minOf(toHour, steps.last().endHour), 0.0)
}

/**
 * A tick's label, placed.
 *
 * @property tick the tick
 * @property left the label's left edge in pixels from the chart's left
 * @property width the label's width in pixels
 */
internal data class PlacedTick(val tick: SolarChart.Tick, val left: Float, val width: Float)

/** Which ticks the time axis marks, so that their labels never overlap, however large the font or small the chart. */
internal object TimeAxis {

    /** The clock hours between two ticks, densest first; every third hour as on the desktop. */
    val EVERY_HOURS = listOf(3, 6, 12)

    /**
     * The densest ticks whose labels fit side by side: every third hour, or fewer when the labels are wide, as at a
     * large font size or on a narrow chart. Each label is centred under its hour, but kept within the chart.
     *
     * @param chart the day's chart, with every whole hour as a tick
     * @param plot where the plot lies
     * @param width the chart's whole width in pixels; the labels stay within it
     * @param gap the least room between two labels in pixels
     * @param labelWidth the width of a tick's label in pixels
     * @return the ticks to mark, in time order; when even every twelfth hour does not fit, as many of those as fit
     *     from the left
     */
    fun fit(chart: SolarChart, plot: PlotArea, width: Float, gap: Float,
        labelWidth: (SolarChart.Tick) -> Float): List<PlacedTick> {
        for (every in EVERY_HOURS) {
            val placed = chart.ticksEvery(every).map { place(it, plot, width, labelWidth(it)) }
            if (placed.zipWithNext().all { (one, next) -> fits(one, next, gap) }) {
                return placed
            }
        }
        val kept = mutableListOf<PlacedTick>()
        chart.ticksEvery(EVERY_HOURS.last()).map { place(it, plot, width, labelWidth(it)) }.forEach { tick ->
            if (kept.isEmpty() || fits(kept.last(), tick, gap)) {
                kept += tick
            }
        }
        return kept
    }

    private fun place(tick: SolarChart.Tick, plot: PlotArea, width: Float, labelWidth: Float) =
        PlacedTick(tick, (plot.x(tick.hour) - labelWidth / 2).coerceIn(0f, max(0f, width - labelWidth)), labelWidth)

    private fun fits(one: PlacedTick, next: PlacedTick, gap: Float) = one.left + one.width + gap <= next.left
}

/**
 * Where the battery bar draws its levels and its marks, and their words under it.
 *
 * @property levelEnd where the level at the start ends, in pixels from the bar's left
 * @property afterEnd where the level after charging ends, in pixels from the bar's left
 * @property minimum the minimum charge level's mark
 * @property target the target charge level's mark
 */
internal data class BatteryBarLayout(val levelEnd: Float, val afterEnd: Float, val minimum: Mark, val target: Mark) {

    /**
     * A mark across the bar, with its words centred under it.
     *
     * @property x where the mark is, in pixels from the bar's left
     * @property labelLeft the left edge of its words, in pixels from the bar's left
     * @property row the line its words are on under the bar: 0, or 1 when they would overlap the other mark's
     */
    data class Mark(val x: Float, val labelLeft: Float, val row: Int)

    /** The lines of words under the bar: 1, or 2 when the marks' words would overlap on one. */
    val rows: Int get() = maxOf(minimum.row, target.row) + 1

    companion object {

        /**
         * The layout of one day's bar.
         *
         * @param battery the day's levels at its start and after its charging
         * @param levels the minimum and target charge levels
         * @param width the bar's width in pixels, 0 or more
         * @param minimumLabel the width of the minimum's words in pixels
         * @param targetLabel the width of the target's words in pixels
         * @param gap the least room between the two marks' words in pixels
         * @return the layout; the target's words go on a second line when they would overlap the minimum's
         */
        fun of(battery: BatteryLevels, levels: ChargeLevels, width: Float, minimumLabel: Float, targetLabel: Float,
            gap: Float): BatteryBarLayout {
            val minimumX = x(levels.minimum.value(), width)
            val targetX = x(levels.target.value(), width)
            val minimumLeft = labelLeft(minimumX, minimumLabel, width)
            val targetLeft = labelLeft(targetX, targetLabel, width)
            val apart = minimumLeft + minimumLabel + gap <= targetLeft || targetLeft + targetLabel + gap <= minimumLeft
            return BatteryBarLayout(x(battery.startPct.value(), width), x(battery.afterPct.value(), width),
                Mark(minimumX, minimumLeft, 0), Mark(targetX, targetLeft, if (apart) 0 else 1))
        }

        /**
         * Where a level is on a bar.
         *
         * @param pct the level in percent; outside 0 to 100 goes on the nearest end
         * @param width the bar's width in pixels
         * @return pixels from the bar's left
         */
        fun x(pct: Double, width: Float): Float = width * (pct.coerceIn(0.0, 100.0) / 100).toFloat()

        private fun labelLeft(markX: Float, labelWidth: Float, width: Float): Float =
            (markX - labelWidth / 2).coerceIn(0f, max(0f, width - labelWidth))
    }
}
