package nl.enadeag.carchargeplanner.android.ui.home

import kotlin.math.max
import nl.enadeag.carchargeplanner.android.home.BatteryLevels
import nl.enadeag.carchargeplanner.android.home.ChargeLevels
import nl.enadeag.carchargeplanner.android.ui.ChartInk
import nl.enadeag.carchargeplanner.engine.config.Percent
import nl.enadeag.carchargeplanner.engine.dashboard.DayChart

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
internal fun curveOf(steps: List<DayChart.Step>, fromHour: Double, toHour: Double): List<CurvePoint> {
    if (steps.isEmpty()) {
        return listOf(CurvePoint(fromHour, 0.0), CurvePoint(toHour, 0.0))
    }
    return listOf(CurvePoint(max(fromHour, steps.first().startHour()), 0.0)) +
        steps.map { CurvePoint(it.midHour.coerceIn(fromHour, toHour), it.kw()) } +
        CurvePoint(minOf(toHour, steps.last().endHour()), 0.0)
}

/** The middle of a step, where the curve passes through its power. */
private val DayChart.Step.midHour: Double get() = (startHour() + endHour()) / 2

/**
 * A tick's label, placed.
 *
 * @property tick the tick
 * @property left the label's left edge in pixels from the chart's left
 * @property width the label's width in pixels
 */
internal data class PlacedTick(val tick: DayChart.Tick, val left: Float, val width: Float)

/** Which ticks the time axis marks, so that their labels never overlap, however large the font or small the chart. */
internal object TimeAxis {

    /** The clock hours between two ticks, densest first; every third hour as on the desktop. */
    val EVERY_HOURS = listOf(3, 6, 12)

    /**
     * The densest ticks whose labels fit side by side: every third hour, or fewer when the labels are wide, as at a
     * large font size or on a narrow chart. Each label is centred under its hour, but kept within the plot's width,
     * as on the desktop.
     *
     * @param chart the day's chart, with every whole hour as a tick
     * @param plot where the plot lies; the labels stay between its left and right edges when they fit there
     * @param gap the least room between two labels in pixels
     * @param labelWidth the width of a tick's label in pixels
     * @return the ticks to mark, in time order; when even every twelfth hour does not fit, as many of those as fit
     *     from the left
     */
    fun fit(chart: DayChart, plot: PlotArea, gap: Float, labelWidth: (DayChart.Tick) -> Float): List<PlacedTick> {
        for (every in EVERY_HOURS) {
            val placed = ticksEvery(chart, every).map { place(it, plot, labelWidth(it)) }
            if (placed.zipWithNext().all { (one, next) -> fits(one, next, gap) }) {
                return placed
            }
        }
        val kept = mutableListOf<PlacedTick>()
        ticksEvery(chart, EVERY_HOURS.last()).map { place(it, plot, labelWidth(it)) }.forEach { tick ->
            if (kept.isEmpty() || fits(kept.last(), tick, gap)) {
                kept += tick
            }
        }
        return kept
    }

    /**
     * The ticks of every so many clock hours, such as 00, 03, 06.
     *
     * @param chart the day's chart, with every whole hour as a tick
     * @param hours the clock hours between two ticks, more than 0
     * @return the ticks whose clock hour is a multiple of [hours], in time order
     */
    fun ticksEvery(chart: DayChart, hours: Int): List<DayChart.Tick> =
        chart.ticks().filter { it.clockHour() % hours == 0 }

    private fun place(tick: DayChart.Tick, plot: PlotArea, labelWidth: Float) = PlacedTick(tick,
        (plot.x(tick.hour()) - labelWidth / 2).coerceIn(plot.left, max(plot.left, plot.right - labelWidth)), labelWidth)

    private fun fits(one: PlacedTick, next: PlacedTick, gap: Float) = one.left + one.width + gap <= next.left
}

/**
 * A point of a drawing.
 *
 * @property x pixels from the drawing's left
 * @property y pixels from the drawing's top
 */
internal data class Point(val x: Float, val y: Float)

/** A shape of a chart's drawing, in pixels, in one of the charts' colours. */
internal sealed interface Shape {

    /** The colour it is drawn in. */
    val ink: ChartInk

    /**
     * A straight line.
     *
     * @property from where it starts
     * @property to where it ends
     * @property ink the colour it is drawn in
     * @property width its width in pixels
     * @property dashed whether it is dashed rather than whole
     * @property round whether its ends are round rather than cut off
     */
    data class Line(val from: Point, val to: Point, override val ink: ChartInk, val width: Float,
        val dashed: Boolean = false, val round: Boolean = false) : Shape

    /**
     * A rectangle, filled or outlined.
     *
     * @property left its left edge
     * @property top its top edge
     * @property width its width in pixels
     * @property height its height in pixels
     * @property ink the colour it is drawn in
     * @property corner the radius of its round corners in pixels; 0 for square ones
     * @property outline the width of its outline in pixels; null when it is filled
     */
    data class Rect(val left: Float, val top: Float, val width: Float, val height: Float,
        override val ink: ChartInk, val corner: Float = 0f, val outline: Float? = null) : Shape

    /**
     * An area within lines from point to point, closed back to the first.
     *
     * @property points its corners in order
     * @property ink the colour it is drawn in
     * @property outline the width of its outline in pixels; null when it is filled
     */
    data class Area(val points: List<Point>, override val ink: ChartInk, val outline: Float? = null) : Shape
}

/**
 * The solar chart's drawing, as the desktop's: the grid, the charge windows, the solar curve, the house's line and
 * "now", in the order they are drawn, and where its words go.
 *
 * @property plot where the plot lies
 * @property shapes what is drawn, in order, under the words
 * @property ticks the time axis's labels, placed
 * @property tickTop the top of the time axis's labels
 * @property scaleAt the top left of the scale's words
 * @property zeroAt the top left of the 0 at the foot of the scale
 * @property nowAt the top left of "now"; null when the chart has no now
 */
internal data class SolarDrawing(val plot: PlotArea, val shapes: List<Shape>, val ticks: List<PlacedTick>,
    val tickTop: Float, val scaleAt: Point, val zeroAt: Point, val nowAt: Point?) {

    companion object {

        /** The room between the words and the plot, and half the least room between two labels of the axis. */
        private const val GAP_DP = 4f

        /** The width of the solar curve's line. */
        private const val CURVE_DP = 2f

        /** The width of a charge window's mark on the axis. */
        private const val WINDOW_MARK_DP = 4f

        /** The width of the dashed lines: the house's use and now. */
        private const val DASHED_DP = 1.5f

        /** The width of the grid's lines, in pixels: a hairline. */
        private const val GRID_PX = 1f

        /**
         * The drawing of a day's chart at a size.
         *
         * @param chart the day's chart
         * @param width the chart's width in pixels, 0 or more
         * @param height the chart's height in pixels, 0 or more
         * @param dp the pixels in a dp, more than 0
         * @param lineHeight the height of a line of the axis's words in pixels
         * @param scaleWidth the width of the scale's words, such as "4 kW", in pixels
         * @param zeroWidth the width of the 0 at the foot of the scale in pixels
         * @param nowWidth the width of "now" in pixels
         * @param tickWidth the width of a tick's label in pixels
         * @return the drawing; the plot is as wide as the chart leaves beside the scale's words, and as high as it
         *     leaves above the axis's labels
         */
        fun of(chart: DayChart, width: Float, height: Float, dp: Float, lineHeight: Float, scaleWidth: Float,
            zeroWidth: Float, nowWidth: Float, tickWidth: (DayChart.Tick) -> Float): SolarDrawing {
            val gap = GAP_DP * dp
            val left = max(scaleWidth, zeroWidth) + gap
            val plot = PlotArea(left, lineHeight / 2, max(0f, width - left - gap),
                max(0f, height - lineHeight * 1.5f - gap), chart.fromHour(), chart.toHour(), chart.scaleKw())
            val grid = listOf(chart.scaleKw(), chart.scaleKw() / 2, 0.0).map {
                Shape.Line(Point(plot.left, plot.y(it)), Point(plot.right, plot.y(it)), ChartInk.GRID_LINE, GRID_PX)
            }
            val windows = chart.windows().flatMap {
                val start = plot.x(it.startHour())
                val end = plot.x(it.endHour())
                listOf(Shape.Rect(start, plot.top, end - start, plot.height, ChartInk.CAR_AREA),
                    Shape.Line(Point(start, plot.bottom), Point(end, plot.bottom), ChartInk.CAR, WINDOW_MARK_DP * dp,
                        round = true))
            }
            val curve = curveOf(chart.steps(), chart.fromHour(), chart.toHour())
                .map { Point(plot.x(it.hour), plot.y(it.kw)) }
            val houseY = plot.y(chart.baseloadKw())
            val house = Shape.Line(Point(plot.left, houseY), Point(plot.right, houseY), ChartInk.MUTED, DASHED_DP * dp,
                dashed = true)
            val nowX = chart.now().orElse(null)?.let { plot.x(it.hour()) }
            val now = nowX?.let {
                Shape.Line(Point(it, plot.top), Point(it, plot.bottom), ChartInk.TEXT, DASHED_DP * dp, dashed = true)
            }
            val shapes = grid + windows + Shape.Area(curve, ChartInk.SOLAR_AREA) +
                Shape.Area(curve, ChartInk.SOLAR, CURVE_DP * dp) + house + listOfNotNull(now)
            return SolarDrawing(plot, shapes, TimeAxis.fit(chart, plot, gap * 2, tickWidth), plot.bottom + gap,
                Point(0f, plot.top - lineHeight / 2), Point(0f, plot.bottom - lineHeight / 2),
                nowX?.let { Point(minOf(it + gap / 2, plot.right - nowWidth), plot.top) })
        }
    }
}

/**
 * Where the battery bar draws its levels and its marks, and their words under it.
 *
 * @property width the bar's width in pixels, 0 or more
 * @property dp the pixels in a dp, more than 0
 * @property line the height of a line of the marks' words in pixels
 * @property levelEnd where the level at the start ends, in pixels from the bar's left
 * @property afterEnd where the level after charging ends, in pixels from the bar's left
 * @property minimum the minimum charge level's mark
 * @property target the target charge level's mark
 */
internal data class BatteryBarLayout(val width: Float, val dp: Float, val line: Float, val levelEnd: Float,
    val afterEnd: Float, val minimum: Mark, val target: Mark) {

    /** The line under the bar that a mark's words are on. */
    enum class Row {

        /** Right under the bar. */
        FIRST,

        /** Under the first, when the words would overlap the other mark's. */
        SECOND,
    }

    /**
     * A mark across the bar, with its words centred under it.
     *
     * @property x where the mark is, in pixels from the bar's left
     * @property labelLeft the left edge of its words, in pixels from the bar's left
     * @property row the line its words are on
     */
    data class Mark(val x: Float, val labelLeft: Float, val row: Row)

    /** The lines of words under the bar: 1, or 2 when the marks' words would overlap on one. */
    val rows: Int get() = maxOf(minimum.row.ordinal, target.row.ordinal) + 1

    private val overhang: Float get() = OVERHANG_DP * dp

    private val bar: Float get() = BAR_DP * dp

    private val labelsTop: Float get() = (OVERHANG_DP * 2 + BAR_DP + GAP_DP / 2) * dp

    /** The height of the bar with its marks and the lines of their words, in pixels. */
    val height: Float get() = labelsTop + line * rows

    /**
     * The top of a mark's words.
     *
     * @param mark one of the two marks
     * @return pixels from the bar's top
     */
    fun labelTop(mark: Mark): Float = labelsTop + line * mark.row.ordinal

    /**
     * What is drawn, in order: the empty track with its outline, the level after charging outlined beyond the level
     * at the start, a line of the ground between the two, and the marks across the bar.
     */
    val shapes: List<Shape> get() {
        val radius = bar / 2
        val after = if (afterEnd > 0) listOf(Shape.Rect(0f, overhang, afterEnd, bar, ChartInk.CAR_AFTER, radius),
            Shape.Rect(0f, overhang, afterEnd, bar, ChartInk.CAR, radius, AFTER_OUTLINE_DP * dp)) else listOf()
        val level = if (levelEnd > 0) listOf(Shape.Rect(0f, overhang, levelEnd, bar, ChartInk.CAR, radius))
            else listOf()
        // the two greens are too close to tell apart by contrast alone
        val between = if (afterEnd > levelEnd && levelEnd > 0) listOf(Shape.Line(Point(levelEnd, overhang),
            Point(levelEnd, overhang + bar), ChartInk.GROUND, LINE_DP * dp)) else listOf()
        val marks = listOf(minimum, target).map {
            Shape.Line(Point(it.x, 0f), Point(it.x, overhang * 2 + bar), ChartInk.TEXT, LINE_DP * dp)
        }
        return listOf(Shape.Rect(0f, overhang, width, bar, ChartInk.GRID_LINE, radius),
            Shape.Rect(0f, overhang, width, bar, ChartInk.MUTED, radius, TRACK_OUTLINE_DP * dp)) +
            after + level + between + marks
    }

    companion object {

        private const val OVERHANG_DP = 4f
        private const val BAR_DP = 16f
        private const val GAP_DP = 8f
        private const val LINE_DP = 2f
        private const val AFTER_OUTLINE_DP = 1.5f
        private const val TRACK_OUTLINE_DP = 1f

        /**
         * The layout of one day's bar.
         *
         * @param battery the day's levels at its start and after its charging
         * @param levels the minimum and target charge levels
         * @param width the bar's width in pixels, 0 or more
         * @param minimumLabel the width of the minimum's words in pixels
         * @param targetLabel the width of the target's words in pixels
         * @param line the height of a line of the marks' words in pixels
         * @param dp the pixels in a dp, more than 0
         * @return the layout; the target's words go on a second line when they would come closer than 8 dp to the
         *     minimum's, which as the lower level are always on the first
         */
        fun of(battery: BatteryLevels, levels: ChargeLevels, width: Float, minimumLabel: Float, targetLabel: Float,
            line: Float, dp: Float): BatteryBarLayout {
            val minimumX = x(levels.minimum, width)
            val targetX = x(levels.target, width)
            val minimumLeft = labelLeft(minimumX, minimumLabel, width)
            val targetLeft = labelLeft(targetX, targetLabel, width)
            val apart = minimumLeft + minimumLabel + GAP_DP * dp <= targetLeft
            return BatteryBarLayout(width, dp, line, x(battery.startPct, width), x(battery.afterPct, width),
                Mark(minimumX, minimumLeft, Row.FIRST), Mark(targetX, targetLeft, if (apart) Row.FIRST else Row.SECOND))
        }

        /**
         * Where a level is on a bar.
         *
         * @param level the level
         * @param width the bar's width in pixels
         * @return pixels from the bar's left
         */
        fun x(level: Percent, width: Float): Float = width * (level.value() / 100).toFloat()

        private fun labelLeft(markX: Float, labelWidth: Float, width: Float): Float =
            (markX - labelWidth / 2).coerceIn(0f, max(0f, width - labelWidth))
    }
}
