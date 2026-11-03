package nl.enadeag.carchargeplanner.android.home

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Optional
import nl.enadeag.carchargeplanner.engine.config.Percent
import nl.enadeag.carchargeplanner.engine.plan.GridAdvice
import nl.enadeag.carchargeplanner.engine.plan.Window
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class DayMapperTest {

    private val amsterdam = ZoneId.of("Europe/Amsterdam")
    private val today = LocalDate.of(2026, 6, 1)

    private fun days(vararg windows: List<Window>, assumed: Boolean = true,
        gridAdvice: GridAdvice? = null): List<DayModel> {
        val planned = Plans.planned(today.atTime(8, 0).atZone(amsterdam), windows.toList(), assumed, gridAdvice)
        return DayMapper(planned).map(planned.days())
    }

    @Test
    fun theFirstWindowIsTheMainOneWithWhatItChargesAndTheLaterOnesAreListed() {
        val morning = Plans.window(amsterdam, today, 9, 10)
        val afternoon = Plans.window(amsterdam, today, 13, 15)

        val day = days(listOf(morning, afternoon)).single()

        assertThat(day.kind).isEqualTo(DayKind.SOLAR)
        assertThat(day.main).isEqualTo(morning)
        assertThat(day.later).containsExactly(afternoon)
        assertThat(day.energyKwh).isCloseTo(1.38, within(1e-9))
        assertThat(day.solarSharePct).isCloseTo(100.0, within(1e-9))
        assertThat(day.battery).isEqualTo(BatteryLevels(Percent(40.0), Percent(60.0)))
    }

    @Test
    fun theEnergyAndTheSolarShareCountWhatComesFromTheGrid() {
        val day = days(listOf(Plans.window(amsterdam, today, 13, 15, importKwh = 0.69))).single()

        assertThat(day.energyKwh).isCloseTo(2.76, within(1e-9))
        assertThat(day.solarSharePct).isCloseTo(75.0, within(1e-9))
        assertThat(day.gridKwh).isCloseTo(0.69, within(1e-9))
    }

    @ParameterizedTest
    @CsvSource("0.0, false", "0.049, false", "0.05, true", "0.3, true")
    fun gridEnergyWithinTheForecastsPrecisionIsNotShown(importKwh: Double, shown: Boolean) {
        val day = days(listOf(Plans.window(amsterdam, today, 13, 15, importKwh))).single()

        assertThat(day.gridKwh).isEqualTo(if (shown) importKwh else null)
    }

    @Test
    fun theAssumedBatteryLevelIsTodaysNoteOnly() {
        val assumed = days(listOf(), listOf())

        assertThat(assumed.map { it.assumedLevel }).containsExactly(Percent(40.0), null)
        assertThat(days(listOf(), assumed = false).single().assumedLevel).isNull()
    }

    @Test
    fun aDayWithoutAWindowIsTheGridAdvicesDayOrHasNone() {
        val advice = GridAdvice(today.plusDays(1), 12.0, 8.0, 4.2, Optional.empty(), Optional.empty())

        val days = days(listOf(), listOf(), listOf(Plans.window(amsterdam, today.plusDays(2), 11, 12)),
            gridAdvice = advice)

        assertThat(days.map { it.kind }).containsExactly(DayKind.NONE, DayKind.GRID, DayKind.SOLAR)
        assertThat(days[0].main).isNull()
        assertThat(days[0].energyKwh).isNull()
        assertThat(days[0].solarSharePct).isNull()
        assertThat(days[0].gridKwh).isNull()
        assertThat(days[0].battery).isEqualTo(BatteryLevels(Percent(40.0), Percent(40.0)))
    }

    @Test
    fun theDaysAreNamedFromTheDayThePlanWasMadeInTheHomesZone() {
        // 23:30 in UTC is already the next day in Amsterdam, which is the plan's today
        val madeAt = Instant.parse("2026-06-01T23:30:00Z").atZone(amsterdam)
        val planned = Plans.planned(madeAt, listOf(listOf(), listOf(), listOf()))

        val days = DayMapper(planned).map(planned.days())

        assertThat(days.map { it.name }).containsExactly(DayName.Today, DayName.Tomorrow,
            DayName.Other(LocalDate.of(2026, 6, 4)))
        assertThat(days.first().assumedLevel).isEqualTo(Percent(40.0))
    }

    @ParameterizedTest
    @CsvSource("2026-03-29T00:30:00Z, 2026-03-29", "2026-10-25T00:30:00Z, 2026-10-25")
    fun onTheShortAndTheLongDayTodayIsTheHomesDay(madeAt: Instant, home: LocalDate) {
        val planned = Plans.planned(madeAt.atZone(amsterdam), listOf(listOf(Plans.window(amsterdam, home, 13, 15)),
            listOf()))

        val days = DayMapper(planned).map(planned.days())

        assertThat(days.map { it.date to it.name }).containsExactly(home to DayName.Today,
            home.plusDays(1) to DayName.Tomorrow)
    }
}
