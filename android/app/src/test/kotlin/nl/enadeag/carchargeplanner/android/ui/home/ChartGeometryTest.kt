package nl.enadeag.carchargeplanner.android.ui.home

import java.time.Instant
import nl.enadeag.carchargeplanner.android.home.BatteryLevels
import nl.enadeag.carchargeplanner.android.home.ChargeLevels
import nl.enadeag.carchargeplanner.android.home.SolarChart
import nl.enadeag.carchargeplanner.engine.config.Percent
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

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
    fun theCurvePassesThroughEachStepsMiddleAndClosesAtNothingWithinTheHoursShown() {
        val steps = listOf(SolarChart.Step(5.0, 6.0, 0.1), SolarChart.Step(6.0, 7.0, 1.0),
            SolarChart.Step(7.0, 8.0, 2.0), SolarChart.Step(15.0, 17.0, 0.5))

        assertThat(curveOf(steps, 6.0, 16.0)).containsExactly(CurvePoint(6.0, 0.0), CurvePoint(6.0, 0.1),
            CurvePoint(6.5, 1.0), CurvePoint(7.5, 2.0), CurvePoint(16.0, 0.5), CurvePoint(16.0, 0.0))
        assertThat(curveOf(steps.subList(1, 3), 6.0, 16.0).let { listOf(it.first(), it.last()) })
            .containsExactly(CurvePoint(6.0, 0.0), CurvePoint(8.0, 0.0))
    }

    @Test
    fun withoutStepsTheCurveIsFlatAtNothingOverTheHoursShown() {
        assertThat(curveOf(listOf(), 6.0, 16.0)).containsExactly(CurvePoint(6.0, 0.0), CurvePoint(16.0, 0.0))
    }

    /** Every hour from 6:00 to 16:00 as a tick, on a plain day. */
    private val chart = SolarChart(6.0, 16.0, 4.0, listOf(), 0.3, listOf(), null,
        (6..16).map { SolarChart.Tick(it.toDouble(), it, Instant.EPOCH.plusSeconds(it * 3600L)) }, null)

    private fun fit(labelWidth: Float, width: Float = 140f, plot: PlotArea = this.plot) =
        TimeAxis.fit(chart, plot, width, 4f) { labelWidth }

    @Test
    fun theAxisMarksEveryThirdHourWhenTheLabelsFit() {
        assertThat(fit(20f).map { it.tick.clockHour }).containsExactly(6, 9, 12, 15)
    }

    @Test
    fun widerLabelsAsAtTwiceTheFontSizeMarkFewerHours() {
        val normal = fit(20f)
        val twice = fit(40f)

        assertThat(twice.map { it.tick.clockHour }).containsExactly(6, 12)
        assertThat(twice.size).isLessThan(normal.size)
    }

    @ParameterizedTest
    @CsvSource("10", "20", "26", "27", "40", "60", "90", "200")
    fun noTwoLabelsOverlapAndEachStaysWithinTheChart(labelWidth: Float) {
        val placed = fit(labelWidth)

        placed.zipWithNext().forEach { (one, next) ->
            assertThat(one.left + one.width + 4f).isLessThanOrEqualTo(next.left)
        }
        placed.forEach {
            assertThat(it.left).isGreaterThanOrEqualTo(0f)
            assertThat(it.left + it.width).isLessThanOrEqualTo(maxOf(140f, it.width))
        }
    }

    @Test
    fun aLabelIsCentredUnderItsHourButKeptWithinTheChart() {
        val placed = fit(20f).associate { it.tick.clockHour to it.left }

        assertThat(placed[9]).isEqualTo(40f)
        assertThat(placed[6]).isEqualTo(10f)
        val atTheEdge = TimeAxis.fit(chart, PlotArea(0f, 0f, 100f, 50f, 6.0, 16.0, 4.0), 100f, 4f) { 20f }
        assertThat(atTheEdge.first().left).isEqualTo(0f)
    }

    @Test
    fun whenNotEvenEveryTwelfthHourFitsAsManyAsFitAreMarkedFromTheLeft() {
        val day = SolarChart(0.0, 24.0, 4.0, listOf(), 0.3, listOf(), null,
            (0..24).map { SolarChart.Tick(it.toDouble(), it, Instant.EPOCH.plusSeconds(it * 3600L)) }, null)

        val placed = TimeAxis.fit(day, PlotArea(0f, 0f, 60f, 50f, 0.0, 24.0, 4.0), 60f, 4f) { 25f }

        assertThat(placed.map { it.tick.clockHour }).containsExactly(0, 24)
    }

    private val battery = BatteryLevels(Percent(40.0), Percent(60.0))

    @Test
    fun theBatteryBarsLevelsAndMarksAreAtTheirPercentages() {
        val layout = BatteryBarLayout.of(battery, ChargeLevels(Percent(20.0), Percent(80.0)), 200f, 30f, 30f, 8f)

        assertThat(layout.levelEnd).isCloseTo(80f, within(0.01f))
        assertThat(layout.afterEnd).isCloseTo(120f, within(0.01f))
        assertThat(layout.minimum).isEqualTo(BatteryBarLayout.Mark(40f, 25f, 0))
        assertThat(layout.target).isEqualTo(BatteryBarLayout.Mark(160f, 145f, 0))
        assertThat(layout.rows).isEqualTo(1)
    }

    @Test
    fun aMarksWordsStayWithinTheBar() {
        val layout = BatteryBarLayout.of(battery, ChargeLevels(Percent(0.0), Percent(100.0)), 200f, 30f, 50f, 8f)

        assertThat(layout.minimum).isEqualTo(BatteryBarLayout.Mark(0f, 0f, 0))
        assertThat(layout.target).isEqualTo(BatteryBarLayout.Mark(200f, 150f, 0))
    }

    @ParameterizedTest
    @CsvSource("30, 70, 0", "30, 50, 1", "70, 75, 1", "75, 50, 1", "75, 25, 0")
    fun theTargetsWordsGoOnASecondLineWhenTheyWouldOverlapTheMinimums(minimum: Double, target: Double, row: Int) {
        // 60 px of words on a bar of 200 px: two marks need 34 points between them
        val layout = BatteryBarLayout.of(battery, ChargeLevels(Percent(minimum), Percent(target)), 200f, 60f, 60f, 8f)

        assertThat(layout.minimum.row).isEqualTo(0)
        assertThat(layout.target.row).isEqualTo(row)
        assertThat(layout.rows).isEqualTo(row + 1)
    }

    @ParameterizedTest
    @CsvSource("-5.0, 0.0", "0.0, 0.0", "50.0, 100.0", "100.0, 200.0", "120.0, 200.0")
    fun aLevelOutsideNothingToAFullBatteryIsOnTheBarsNearestEnd(pct: Double, x: Float) {
        assertThat(BatteryBarLayout.x(pct, 200f)).isEqualTo(x)
    }
}
