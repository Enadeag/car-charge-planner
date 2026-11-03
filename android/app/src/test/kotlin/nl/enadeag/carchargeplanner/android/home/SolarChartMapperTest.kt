package nl.enadeag.carchargeplanner.android.home

import java.time.LocalDate
import java.time.ZoneId
import nl.enadeag.carchargeplanner.engine.Outlook
import nl.enadeag.carchargeplanner.engine.plan.Window
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class SolarChartMapperTest {

    private val amsterdam = ZoneId.of("Europe/Amsterdam")
    private val today = LocalDate.of(2026, 6, 1)

    /** Sun from 07:00 to 17:00, at most 3.2 kW at 12:00. */
    private val sunny = doubleArrayOf(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.005, 0.4, 1.0, 1.8, 2.5, 3.0, 3.2, 3.0, 2.4,
        1.6, 0.8, 0.2, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)

    private fun planned(madeAt: Int = 9, days: List<List<Window>> = listOf(listOf(), listOf()),
        baseloadKw: Double = 0.3, kw: (LocalDate) -> DoubleArray = { sunny }): Outlook.Planned =
        Plans.planned(today.atTime(madeAt, 0).atZone(amsterdam), days, baseloadKw = baseloadKw,
            forecast = { Plans.forecast(amsterdam, it, *kw(it)) })

    private fun charts(planned: Outlook.Planned) = planned.days().map(SolarChartMapper(planned)::map)

    @Test
    fun theChartShowsFromTheHourBeforeTheFirstSunToTheHourAfterTheLastWithTheStepsInThoseHours() {
        val chart = charts(planned()).first()

        // 0.005 kW at 06:00 is no sun: the first sun is at 07:00, the last ends at 18:00
        assertThat(chart.fromHour).isEqualTo(6.0)
        assertThat(chart.toHour).isEqualTo(19.0)
        assertThat(chart.steps.map { it.startHour }).containsExactly(6.0, 7.0, 8.0, 9.0, 10.0, 11.0, 12.0, 13.0,
            14.0, 15.0, 16.0, 17.0, 18.0)
        assertThat(chart.steps[6]).isEqualTo(SolarChart.Step(12.0, 13.0, 3.2))
    }

    @Test
    fun aDayWithoutSunShowsSixInTheMorningToEightInTheEveningAndHasNoPeak() {
        val chart = charts(planned { DoubleArray(24) { 0.01 } }).first()

        assertThat(chart.fromHour).isEqualTo(6.0)
        assertThat(chart.toHour).isEqualTo(20.0)
        assertThat(chart.peak).isNull()
    }

    @Test
    fun thePeakIsTheHighestStepAtItsStart() {
        val chart = charts(planned()).first()

        assertThat(chart.peak).isEqualTo(SolarChart.Peak(3.2, today.atTime(12, 0).atZone(amsterdam).toInstant()))
    }

    @Test
    fun theSunsHoursStayWithinTheDay() {
        val chart = charts(planned { DoubleArray(24) { 1.0 } }).first()

        assertThat(chart.fromHour).isEqualTo(0.0)
        assertThat(chart.toHour).isEqualTo(24.0)
    }

    @ParameterizedTest
    @CsvSource("3.2, 0.3, 4.0", "0.4, 0.3, 1.0", "0.0, 0.0, 1.0", "2.0, 2.4, 3.0", "4.0, 0.3, 4.0")
    fun everyDayHasOneScaleTheWholeKilowattAboveTheHighestSunOrTheHouse(peak: Double, house: Double, scale: Double) {
        // the peak is on the second day, so the first one's scale shows it is shared
        val charts = charts(planned(baseloadKw = house) {
            if (it == today) DoubleArray(24) { hour -> if (hour == 12) 0.2 else 0.0 }
            else DoubleArray(24) { hour -> if (hour == 12) peak else 0.0 }
        })

        assertThat(charts.map { it.scaleKw }).containsExactly(scale, scale)
        assertThat(charts.first().houseKw).isEqualTo(house)
    }

    @Test
    fun nowIsMarkedOnTodayOnlyAtTheMomentThePlanWasMade() {
        val charts = charts(planned(madeAt = 9))

        assertThat(charts[0].now).isEqualTo(SolarChart.Moment(9.0, today.atTime(9, 0).atZone(amsterdam).toInstant()))
        assertThat(charts[1].now).isNull()
    }

    @Test
    fun aDayBeforeTodayHasNoNowEvenWhenThePlanWasMadeAtTheEndOfItsHoursShown() {
        // made at midnight, with the day before still in the plan and sun until its last hour: 24:00 is shown there
        val before = Plans.planned(today.minusDays(1).atStartOfDay(amsterdam), listOf(listOf(), listOf()),
            forecast = { Plans.forecast(amsterdam, it, *DoubleArray(24) { 1.0 }) })
        val planned = Outlook.Planned(today.atStartOfDay(amsterdam).toInstant(), amsterdam, before.days(),
            before.batteryLevelPct(), true, before.gridAdvice(), listOf(), before.forecastSource(), 0.3,
            before.minimumPct(), before.targetPct())

        val charts = charts(planned)

        assertThat(charts[0].toHour).isEqualTo(24.0)
        assertThat(charts.map { it.now?.hour }).containsExactly(null, 0.0)
    }

    @ParameterizedTest
    @CsvSource("5, false", "6, true", "19, true", "20, false")
    fun nowIsMarkedOnlyWithinTheHoursShown(madeAt: Int, marked: Boolean) {
        assertThat(charts(planned(madeAt = madeAt)).first().now != null).isEqualTo(marked)
    }

    @Test
    fun theChargeWindowsAreAtTheirHours() {
        val windows = listOf(Plans.window(amsterdam, today, 10, 12), Plans.window(amsterdam, today, 14, 15))

        val chart = charts(planned(days = listOf(windows))).single()

        assertThat(chart.windows).containsExactly(SolarChart.Span(10.0, 12.0), SolarChart.Span(14.0, 15.0))
    }

    /** On the short day 02:00 is skipped; on the long day 02:00 to 03:00 comes twice. */
    @ParameterizedTest
    @CsvSource("2026-03-29, 3.0, 23.0", "2026-10-25, 5.0, 25.0")
    fun onTheShortAndTheLongDayAWindowAndTheTicksAreAtTheirRealHours(day: LocalDate, windowEnd: Double,
        dayHours: Double) {
        val planned = Plans.planned(day.atTime(0, 30).atZone(amsterdam), listOf(listOf(
            Plans.window(amsterdam, day, 1, 4))), forecast = { Plans.forecast(amsterdam, it,
            *DoubleArray(dayHours.toInt()) { 1.0 }) })

        val chart = SolarChartMapper(planned).map(planned.days().single())

        assertThat(chart.windows.single()).isEqualTo(SolarChart.Span(1.0, windowEnd))
        assertThat(chart.toHour).isEqualTo(dayHours)
        val three = chart.ticks.single { it.clockHour == 3 }
        assertThat(three.hour).isCloseTo(windowEnd - 1, within(1e-9))
        assertThat(three.at).isEqualTo(day.atTime(3, 0).atZone(amsterdam).toInstant())
        assertThat(chart.ticks.last()).isEqualTo(SolarChart.Tick(dayHours, 24,
            day.plusDays(1).atStartOfDay(amsterdam).toInstant()))
        assertThat(chart.ticksEvery(6).map { it.hour }).containsExactly(0.0, windowEnd + 2, windowEnd + 8,
            windowEnd + 14, dayHours)
    }

    @Test
    fun theShortDayHasNoTickAtTheHourTheClocksSkip() {
        val day = LocalDate.of(2026, 3, 29)
        val planned = Plans.planned(day.atTime(0, 30).atZone(amsterdam), listOf(listOf()),
            forecast = { Plans.forecast(amsterdam, it, *DoubleArray(23) { 1.0 }) })

        val chart = SolarChartMapper(planned).map(planned.days().single())

        assertThat(chart.ticks.map { it.clockHour }).hasSize(24).doesNotContain(2)
    }

    @Test
    fun theTicksAreEveryWholeClockHourShownAndEveryThirdOnesAreTheMultiplesOfThree() {
        val chart = charts(planned()).first()

        assertThat(chart.ticks.map { it.clockHour }).containsExactlyElementsOf(6..19)
        assertThat(chart.ticks.map { it.hour }).containsExactlyElementsOf((6..19).map(Int::toDouble))
        assertThat(chart.ticksEvery(3).map { it.clockHour }).containsExactly(6, 9, 12, 15, 18)
    }

    @Test
    fun aChartRefusesHoursThatDoNotRunForwardsAndAScaleOrHouseOutOfRange() {
        fun chart(from: Double = 6.0, to: Double = 20.0, scale: Double = 1.0, house: Double = 0.3) =
            SolarChart(from, to, scale, listOf(), house, listOf(), null, listOf(), null)

        assertThat(chart().toHour).isEqualTo(20.0)
        listOf({ chart(from = -1.0) }, { chart(to = 6.0) }, { chart(from = Double.NaN) }, { chart(scale = 0.0) },
            { chart(scale = Double.NaN) }, { chart(scale = Double.POSITIVE_INFINITY) }, { chart(house = -0.1) },
            { chart(house = Double.NaN) }, { chart(house = Double.POSITIVE_INFINITY) }).forEach {
            assertThatThrownBy { it() }.isInstanceOf(IllegalArgumentException::class.java)
        }
    }

    @Test
    fun aStepAndAWindowRunForwardsAndAStepsPowerIsNotNegative() {
        listOf({ SolarChart.Step(2.0, 2.0, 1.0) }, { SolarChart.Step(2.0, 3.0, -0.1) },
            { SolarChart.Step(2.0, 3.0, Double.NaN) }, { SolarChart.Span(3.0, 3.0) }).forEach {
            assertThatThrownBy { it() }.isInstanceOf(IllegalArgumentException::class.java)
        }
    }
}
