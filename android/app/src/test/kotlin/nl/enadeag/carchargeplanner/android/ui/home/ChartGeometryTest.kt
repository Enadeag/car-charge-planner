package nl.enadeag.carchargeplanner.android.ui.home

import java.time.Instant
import java.util.Optional
import nl.enadeag.carchargeplanner.android.home.BatteryLevels
import nl.enadeag.carchargeplanner.android.home.ChargeLevels
import nl.enadeag.carchargeplanner.engine.dashboard.DayChart
import nl.enadeag.carchargeplanner.android.ui.ChartInk
import nl.enadeag.carchargeplanner.android.ui.home.BatteryBarLayout.Mark
import nl.enadeag.carchargeplanner.android.ui.home.BatteryBarLayout.Row
import nl.enadeag.carchargeplanner.engine.config.Percent
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

class ChartGeometryTest {

    /** 100 px wide from 6:00 to 16:00, so 10 px an hour, and 50 px high up to 4 kW. */
    private val plot = PlotArea(20f, 5f, 100f, 50f, 6.0, 16.0, 4.0)

    @ParameterizedTest
    @CsvSource("6.0, 20.0", "11.0, 70.0", "16.0, 120.0", "2.0, 20.0", "23.0, 120.0")
    fun anHourIsAcrossThePlotAndOneOutsideItOnItsNearestEdge(hour: Double, x: Float) {
        assertThat(plot.x(hour)).isEqualTo(x)
    }

    @ParameterizedTest
    @CsvSource("0.0, 55.0", "2.0, 30.0", "4.0, 5.0", "-1.0, 55.0", "9.0, 5.0")
    fun aPowerIsUpThePlotAndOneOutsideItsScaleOnItsNearestEdge(kw: Double, y: Float) {
        assertThat(plot.y(kw)).isEqualTo(y)
    }

    @Test
    fun aPlotRefusesANegativeSizeHoursThatDoNotRunForwardsAndAScaleOfNothing() {
        listOf({ PlotArea(0f, 0f, -1f, 1f, 6.0, 7.0, 1.0) }, { PlotArea(0f, 0f, 1f, -1f, 6.0, 7.0, 1.0) },
            { PlotArea(0f, 0f, 1f, 1f, 7.0, 7.0, 1.0) }, { PlotArea(0f, 0f, 1f, 1f, 6.0, 7.0, 0.0) }).forEach {
            assertThatThrownBy { it() }.isInstanceOf(IllegalArgumentException::class.java)
        }
    }

    @Test
    fun aPlotWithoutRoomPutsEveryHourAndPowerOnItsEdges() {
        // a chart squeezed to nothing at a large font size
        val none = PlotArea(20f, 5f, 0f, 0f, 6.0, 16.0, 4.0)

        assertThat(listOf(none.x(6.0), none.x(11.0), none.right)).containsOnly(20f)
        assertThat(listOf(none.y(0.0), none.y(4.0), none.bottom)).containsOnly(5f)
    }

    @Test
    fun theCurvePassesThroughEachStepsMiddleAndClosesAtNothingWithinTheHoursShown() {
        val steps = listOf(DayChart.Step(5.0, 6.0, 0.1, Instant.EPOCH), DayChart.Step(6.0, 7.0, 1.0, Instant.EPOCH),
            DayChart.Step(7.0, 8.0, 2.0, Instant.EPOCH), DayChart.Step(15.0, 17.0, 0.5, Instant.EPOCH))

        assertThat(curveOf(steps, 6.0, 16.0)).containsExactly(CurvePoint(6.0, 0.0), CurvePoint(6.0, 0.1),
            CurvePoint(6.5, 1.0), CurvePoint(7.5, 2.0), CurvePoint(16.0, 0.5), CurvePoint(16.0, 0.0))
        assertThat(curveOf(steps.subList(1, 3), 6.0, 16.0).let { listOf(it.first(), it.last()) })
            .containsExactly(CurvePoint(6.0, 0.0), CurvePoint(8.0, 0.0))
    }

    @Test
    fun withoutStepsTheCurveIsFlatAtNothingOverTheHoursShown() {
        assertThat(curveOf(listOf(), 6.0, 16.0)).containsExactly(CurvePoint(6.0, 0.0), CurvePoint(16.0, 0.0))
    }

    private fun ticks(hours: IntRange) =
        hours.map { DayChart.Tick(it.toDouble(), it, Instant.EPOCH.plusSeconds(it * 3600L)) }

    /** A chart up to 4 kW; its moments are left at the epoch, as the drawing uses only the hours. */
    private fun dayChart(from: Double, to: Double, ticks: List<DayChart.Tick>, steps: List<DayChart.Step> = listOf(),
        baseloadKw: Double = 0.3, windows: List<DayChart.WindowBand> = listOf(), now: Double? = null) =
        DayChart(from, to, 4.0, steps, baseloadKw, windows,
            Optional.ofNullable(now?.let { DayChart.Moment(it, Instant.EPOCH) }), ticks, Optional.empty())

    /** Every hour from 6:00 to 16:00 as a tick, on a plain day. */
    private val chart = dayChart(6.0, 16.0, ticks(6..16))

    /** That day with sun from 9:00 to 10:00, the house at 1 kW, a window from 11:00 to 13:00 and now at 8:00. */
    private val busy = dayChart(6.0, 16.0, ticks(6..16), steps = listOf(DayChart.Step(9.0, 10.0, 2.0, Instant.EPOCH)),
        baseloadKw = 1.0, windows = listOf(DayChart.WindowBand(11.0, 13.0, Instant.EPOCH, Instant.EPOCH)), now = 8.0)

    private fun fit(labelWidth: Float, plot: PlotArea = this.plot) = TimeAxis.fit(chart, plot, 4f) { labelWidth }

    @Test
    fun theAxisMarksEveryThirdHourWhenTheLabelsFit() {
        assertThat(fit(16f).map { it.tick.clockHour() }).containsExactly(6, 9, 12, 15)
    }

    @Test
    fun widerLabelsAsAtTwiceTheFontSizeMarkFewerHours() {
        val normal = fit(16f)
        val twice = fit(32f)

        assertThat(twice.map { it.tick.clockHour() }).containsExactly(6, 12)
        assertThat(twice.size).isLessThan(normal.size)
    }

    @ParameterizedTest
    @CsvSource("10", "16", "20", "26", "27", "40", "60", "90", "200")
    fun noTwoLabelsOverlapAndEachStaysWithinThePlot(labelWidth: Float) {
        val placed = fit(labelWidth)

        placed.zipWithNext().forEach { (one, next) ->
            assertThat(one.left + one.width + 4f).isLessThanOrEqualTo(next.left)
        }
        placed.forEach {
            assertThat(it.left).isGreaterThanOrEqualTo(plot.left)
            assertThat(it.left + it.width).isLessThanOrEqualTo(maxOf(plot.right, plot.left + it.width))
        }
    }

    @Test
    fun aLabelIsCentredUnderItsHourButKeptWithinThePlotAsOnTheDesktop() {
        val placed = fit(16f).associate { it.tick.clockHour() to it.left }
        val toFifteen = TimeAxis.fit(chart, PlotArea(20f, 5f, 90f, 50f, 6.0, 15.0, 4.0), 4f) { 16f }

        assertThat(placed[9]).isEqualTo(42f)
        assertThat(placed[6]).isEqualTo(20f)
        assertThat(toFifteen.last().left).isEqualTo(94f)
    }

    @Test
    fun whenNotEvenEveryTwelfthHourFitsAsManyAsFitAreMarkedFromTheLeft() {
        val day = dayChart(0.0, 24.0, ticks(0..24))

        val placed = TimeAxis.fit(day, PlotArea(0f, 0f, 60f, 50f, 0.0, 24.0, 4.0), 4f) { 25f }

        assertThat(placed.map { it.tick.clockHour() }).containsExactly(0, 24)
    }

    /** From 3:00 to 18:00, so the ticks of 6:00 to 15:00 lie 60 px from either edge, at 20 px an hour. */
    private val wide = dayChart(3.0, 18.0, ticks(6..15))

    @ParameterizedTest
    @CsvSource("56, '6, 9, 12, 15'", "57, '6, 12'")
    fun labelsThatLeaveJustTheGapFitAndOnePixelMoreDoesNot(labelWidth: Float, marked: String) {
        // every third hour is 60 px apart: 56 px of label and 4 px of gap fit exactly
        val placed = TimeAxis.fit(wide, PlotArea(0f, 0f, 300f, 50f, 3.0, 18.0, 4.0), 4f) { labelWidth }

        assertThat(placed.joinToString { it.tick.clockHour().toString() }).isEqualTo(marked)
    }

    @Test
    fun everyLabelMustFitNotOnlyThoseOfOnePair() {
        // noon's label is wider, so it overlaps nine o'clock's while six and nine fit
        val placed = TimeAxis.fit(wide, PlotArea(0f, 0f, 300f, 50f, 3.0, 18.0, 4.0), 4f) {
            if (it.clockHour() == 12) 100f else 56f
        }

        assertThat(placed.map { it.tick.clockHour() }).containsExactly(6, 12)
    }

    /**
     * The drawing of a chart with a scale 16 px wide and a 0 of 8 px, at a dp of [dp] px and lines of 10 px: 124 by 69
     * px at a dp of 1 px leaves the plot of [plot].
     */
    private fun drawing(chart: DayChart, width: Float = 124f, height: Float = 69f, dp: Float = 1f,
        tickWidth: Float = 10f) =
        SolarDrawing.of(chart, width, height, dp, 10f, 16f, 8f, 20f) { tickWidth }

    @Test
    fun thePlotLeavesRoomForTheScaleBesideItAndTheAxisUnderIt() {
        val drawing = drawing(chart, width = 324f, height = 200f, dp = 2f)

        // 8 px, 4 dp, between the scale and the plot, at its right, and between the plot and the axis
        assertThat(drawing.plot).isEqualTo(PlotArea(24f, 5f, 292f, 177f, 6.0, 16.0, 4.0))
        assertThat(drawing.tickTop).isEqualTo(190f)
        assertThat(drawing.scaleAt).isEqualTo(Point(0f, 0f))
        assertThat(drawing.zeroAt).isEqualTo(Point(0f, 177f))
    }

    @ParameterizedTest
    @CsvSource("52, '6, 9, 12, 15'", "53, '6, 12'")
    fun theAxisKeepsEightDpBetweenItsLabels(labelWidth: Float, marked: String) {
        // a plot of 300 px from 20 px on: every third hour is 60 px apart, so 52 px of label and 8 px of gap fit
        val drawing = drawing(wide, width = 324f, tickWidth = labelWidth)

        assertThat(drawing.ticks.joinToString { it.tick.clockHour().toString() }).isEqualTo(marked)
    }

    @Test
    fun theDrawingIsTheGridTheWindowsTheCurveTheHouseAndNowInTheChartsColours() {
        val drawing = drawing(busy)

        val curve = listOf(Point(50f, 55f), Point(55f, 30f), Point(60f, 55f))
        assertThat(drawing.plot).isEqualTo(plot)
        assertThat(drawing.shapes).containsExactly(
            Shape.Line(Point(20f, 5f), Point(120f, 5f), ChartInk.GRID_LINE, 1f),
            Shape.Line(Point(20f, 30f), Point(120f, 30f), ChartInk.GRID_LINE, 1f),
            Shape.Line(Point(20f, 55f), Point(120f, 55f), ChartInk.GRID_LINE, 1f),
            Shape.Rect(70f, 5f, 20f, 50f, ChartInk.CAR_AREA),
            Shape.Line(Point(70f, 55f), Point(90f, 55f), ChartInk.CAR, 4f, round = true),
            Shape.Area(curve, ChartInk.SOLAR_AREA),
            Shape.Area(curve, ChartInk.SOLAR, 2f),
            Shape.Line(Point(20f, 42.5f), Point(120f, 42.5f), ChartInk.MUTED, 1.5f, dashed = true),
            Shape.Line(Point(40f, 5f), Point(40f, 55f), ChartInk.TEXT, 1.5f, dashed = true))
        assertThat(drawing.nowAt).isEqualTo(Point(42f, 5f))
    }

    @ParameterizedTest
    @ValueSource(floats = [1f, 3f])
    fun everySizeOfTheSolarDrawingThatIsInDpGrowsWithTheDp(dp: Float) {
        val gap = 4 * dp
        val left = 16f + gap

        // a plot of 100 by 50 px from 6:00 to 16:00, whatever the dp
        val drawing = drawing(busy, width = left + 100f + gap, height = 15f + gap + 50f, dp = dp)

        fun x(hour: Double) = left + (hour.toFloat() - 6f) * 10f
        val curve = listOf(Point(x(9.0), 55f), Point(x(9.5), 30f), Point(x(10.0), 55f))
        assertThat(drawing.plot).isEqualTo(PlotArea(left, 5f, 100f, 50f, 6.0, 16.0, 4.0))
        assertThat(drawing.shapes).containsExactly(
            Shape.Line(Point(left, 5f), Point(left + 100f, 5f), ChartInk.GRID_LINE, 1f),
            Shape.Line(Point(left, 30f), Point(left + 100f, 30f), ChartInk.GRID_LINE, 1f),
            Shape.Line(Point(left, 55f), Point(left + 100f, 55f), ChartInk.GRID_LINE, 1f),
            Shape.Rect(x(11.0), 5f, 20f, 50f, ChartInk.CAR_AREA),
            Shape.Line(Point(x(11.0), 55f), Point(x(13.0), 55f), ChartInk.CAR, 4f * dp, round = true),
            Shape.Area(curve, ChartInk.SOLAR_AREA),
            Shape.Area(curve, ChartInk.SOLAR, 2f * dp),
            Shape.Line(Point(left, 42.5f), Point(left + 100f, 42.5f), ChartInk.MUTED, 1.5f * dp, dashed = true),
            Shape.Line(Point(x(8.0), 5f), Point(x(8.0), 55f), ChartInk.TEXT, 1.5f * dp, dashed = true))
        assertThat(drawing.nowAt).isEqualTo(Point(x(8.0) + gap / 2, 5f))
        assertThat(drawing.tickTop).isEqualTo(55f + gap)
    }

    @Test
    fun nowsWordsStayWithinThePlotAtItsRightEdge() {
        val late = drawing(dayChart(6.0, 16.0, ticks(6..16), now = 15.9))

        assertThat(late.nowAt).isEqualTo(Point(100f, 5f))
    }

    @Test
    fun aChartWithoutNowDrawsNoLineOfTheTextsColourAndNoWords() {
        val drawing = drawing(chart)

        assertThat(drawing.shapes.map { it.ink }).doesNotContain(ChartInk.TEXT)
        assertThat(drawing.nowAt).isNull()
    }

    private val battery = BatteryLevels(Percent(40.0), Percent(50.0))

    private fun bar(battery: BatteryLevels = this.battery, minimum: Double = 20.0, target: Double = 80.0,
        width: Float = 200f, minimumLabel: Float = 30f, targetLabel: Float = 30f, dp: Float = 1f) =
        BatteryBarLayout.of(battery, ChargeLevels(Percent(minimum), Percent(target)), width, minimumLabel, targetLabel,
            10f, dp)

    @Test
    fun theBatteryBarsLevelsAndMarksAreAtTheirPercentages() {
        val layout = bar()

        assertThat(layout.levelEnd).isCloseTo(80f, within(0.01f))
        assertThat(layout.afterEnd).isCloseTo(100f, within(0.01f))
        assertThat(layout.minimum).isEqualTo(Mark(40f, 25f, Row.FIRST))
        assertThat(layout.target).isEqualTo(Mark(160f, 145f, Row.FIRST))
        assertThat(layout.rows).isEqualTo(1)
    }

    @Test
    fun aMarksWordsStayWithinTheBar() {
        val layout = bar(minimum = 0.0, target = 100.0, targetLabel = 50f)

        assertThat(layout.minimum).isEqualTo(Mark(0f, 0f, Row.FIRST))
        assertThat(layout.target).isEqualTo(Mark(200f, 150f, Row.FIRST))
    }

    @ParameterizedTest
    @CsvSource("30, 70, FIRST", "30, 50, SECOND", "70, 75, SECOND", "75, 75, SECOND")
    fun theTargetsWordsGoOnASecondLineWhenTheyWouldOverlapTheMinimums(minimum: Double, target: Double, row: String) {
        // 60 px of words on a bar of 200 px: two marks need 34 points between them
        val layout = bar(minimum = minimum, target = target, minimumLabel = 60f, targetLabel = 60f)

        assertThat(layout.minimum.row).isEqualTo(Row.FIRST)
        assertThat(layout.target.row).isEqualTo(Row.valueOf(row))
        assertThat(layout.rows).isEqualTo(Row.valueOf(row).ordinal + 1)
    }

    @ParameterizedTest
    @CsvSource("32, FIRST", "34, SECOND")
    fun theMarksWordsThatLeaveJustTheGapStayOnOneLineAndOnePixelMoreDoesNot(minimumLabel: Float, row: String) {
        // marks 40 px apart with words of 32 px: the target's words start just 8 px after the minimum's end
        val layout = bar(minimum = 50.0, target = 75.0, width = 160f, minimumLabel = minimumLabel, targetLabel = 32f)

        assertThat(layout.target.row).isEqualTo(Row.valueOf(row))
    }

    @ParameterizedTest
    @ValueSource(floats = [1f, 3f])
    fun everySizeOfTheBatteryBarThatIsInDpGrowsWithTheDp(dp: Float) {
        val layout = bar(dp = dp)

        assertThat(layout.shapes).containsExactly(
            Shape.Rect(0f, 4f * dp, 200f, 16f * dp, ChartInk.GRID_LINE, 8f * dp),
            Shape.Rect(0f, 4f * dp, 200f, 16f * dp, ChartInk.MUTED, 8f * dp, 1f * dp),
            Shape.Rect(0f, 4f * dp, 100f, 16f * dp, ChartInk.CAR_AFTER, 8f * dp),
            Shape.Rect(0f, 4f * dp, 100f, 16f * dp, ChartInk.CAR, 8f * dp, 1.5f * dp),
            Shape.Rect(0f, 4f * dp, 80f, 16f * dp, ChartInk.CAR, 8f * dp),
            Shape.Line(Point(80f, 4f * dp), Point(80f, 20f * dp), ChartInk.GROUND, 2f * dp),
            Shape.Line(Point(40f, 0f), Point(40f, 24f * dp), ChartInk.TEXT, 2f * dp),
            Shape.Line(Point(160f, 0f), Point(160f, 24f * dp), ChartInk.TEXT, 2f * dp))
        assertThat(layout.height).isEqualTo(28f * dp + 10f)
        assertThat(layout.labelTop(layout.target)).isEqualTo(28f * dp)
        assertThat(bar(minimum = 70.0, target = 75.0, dp = dp).height).isEqualTo(28f * dp + 20f)
    }

    @ParameterizedTest
    @CsvSource("1, 152, FIRST", "1, 154, SECOND", "3, 120, FIRST", "3, 122, SECOND")
    fun theMarksWordsKeepEightDpBetweenThemWhateverTheDp(dp: Float, minimumLabel: Float, row: String) {
        // the target's words start 16 px before its mark, at 284 px, and the minimum's mark is at 200 px: its words
        // may end 8 dp before that
        val layout = bar(minimum = 50.0, target = 75.0, width = 400f, minimumLabel = minimumLabel, targetLabel = 32f,
            dp = dp)

        assertThat(layout.target.row).isEqualTo(Row.valueOf(row))
    }

    @ParameterizedTest
    @CsvSource("0.0, 0.0", "50.0, 100.0", "100.0, 200.0")
    fun aLevelIsAcrossTheBar(pct: Double, x: Float) {
        assertThat(BatteryBarLayout.x(Percent(pct), 200f)).isEqualTo(x)
    }

    @Test
    fun theBarIsHighEnoughForEachLineOfWordsUnderIt() {
        val one = bar()
        val two = bar(minimum = 70.0, target = 75.0)

        // 4 dp over and under the 16 dp bar, then 4 dp of room
        assertThat(one.height).isEqualTo(38f)
        assertThat(one.labelTop(one.target)).isEqualTo(28f)
        assertThat(two.height).isEqualTo(48f)
        assertThat(two.labelTop(two.minimum)).isEqualTo(28f)
        assertThat(two.labelTop(two.target)).isEqualTo(38f)
    }

    private val track = listOf(Shape.Rect(0f, 4f, 200f, 16f, ChartInk.GRID_LINE, 8f),
        Shape.Rect(0f, 4f, 200f, 16f, ChartInk.MUTED, 8f, 1f))

    private fun marks(minimum: Float, target: Float) =
        listOf(Shape.Line(Point(minimum, 0f), Point(minimum, 24f), ChartInk.TEXT, 2f),
            Shape.Line(Point(target, 0f), Point(target, 24f), ChartInk.TEXT, 2f))

    @Test
    fun theBarDrawsTheOutlinedTrackTheLevelAfterChargingBeyondTheLevelALineBetweenThemAndTheMarks() {
        assertThat(bar().shapes).containsExactlyElementsOf(track + listOf(
            Shape.Rect(0f, 4f, 100f, 16f, ChartInk.CAR_AFTER, 8f),
            Shape.Rect(0f, 4f, 100f, 16f, ChartInk.CAR, 8f, 1.5f),
            Shape.Rect(0f, 4f, 80f, 16f, ChartInk.CAR, 8f),
            Shape.Line(Point(80f, 4f), Point(80f, 20f), ChartInk.GROUND, 2f)) + marks(40f, 160f))
    }

    @Test
    fun anEmptyBatteryDrawsOnlyTheTrackAndTheMarks() {
        val empty = bar(BatteryLevels(Percent(0.0), Percent(0.0)))

        assertThat(empty.shapes).containsExactlyElementsOf(track + marks(40f, 160f))
    }

    @Test
    fun anEmptyBatteryThatChargesDrawsTheLevelAfterChargingWithoutALineAtTheStart() {
        val charged = bar(BatteryLevels(Percent(0.0), Percent(50.0)))

        assertThat(charged.shapes).containsExactlyElementsOf(track + listOf(
            Shape.Rect(0f, 4f, 100f, 16f, ChartInk.CAR_AFTER, 8f),
            Shape.Rect(0f, 4f, 100f, 16f, ChartInk.CAR, 8f, 1.5f)) + marks(40f, 160f))
    }

    @Test
    fun aFullBatteryFillsTheTrackWithoutALineBetweenTheLevels() {
        val full = bar(BatteryLevels(Percent(100.0), Percent(100.0)))

        assertThat(full.shapes).containsExactlyElementsOf(track + listOf(
            Shape.Rect(0f, 4f, 200f, 16f, ChartInk.CAR_AFTER, 8f),
            Shape.Rect(0f, 4f, 200f, 16f, ChartInk.CAR, 8f, 1.5f),
            Shape.Rect(0f, 4f, 200f, 16f, ChartInk.CAR, 8f)) + marks(40f, 160f))
    }

    @Test
    fun aMinimumEqualToTheTargetDrawsBothMarksOnOneAnother() {
        val equal = bar(minimum = 80.0, target = 80.0)

        assertThat(equal.shapes.takeLast(2)).containsExactlyElementsOf(marks(160f, 160f))
        assertThat(equal.rows).isEqualTo(2)
    }
}
