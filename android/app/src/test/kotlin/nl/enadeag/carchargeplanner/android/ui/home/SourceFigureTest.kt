package nl.enadeag.carchargeplanner.android.home

import java.time.LocalDate
import java.time.ZoneId
import java.util.Optional
import nl.enadeag.carchargeplanner.engine.config.Percent
import nl.enadeag.carchargeplanner.engine.dashboard.BatteryLevels
import nl.enadeag.carchargeplanner.engine.dashboard.DashboardDay
import nl.enadeag.carchargeplanner.engine.dashboard.EnergySource
import nl.enadeag.carchargeplanner.engine.plan.Window
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SourceFigureTest {

    private val amsterdam = ZoneId.of("Europe/Amsterdam")
    private val today = LocalDate.of(2026, 6, 1)

    private fun day(windows: List<Window>): DashboardDay {
        val planned = Plans.planned(today.atTime(8, 0).atZone(amsterdam), listOf(windows))
        return planned.dashboard(listOf(), listOf()).days().single()
    }

    @Test
    fun aDayShowsItsFiguresEachInItsOwnSourcesColoursSolarBatteryGrid() {
        val day = day(listOf(Plans.window(amsterdam, today, 13, 15, importKwh = 0.69)))

        assertThat(SourceFigure.of(day).map { it.source to it }).containsExactly(
            EnergySource.SOLAR to SourceFigure.SolarShare(day.main().orElseThrow().window().solarSharePct()),
            EnergySource.CAR to SourceFigure.Battery(BatteryLevels(Percent(40.0), Percent(50.0), Optional.empty())),
            EnergySource.GRID to SourceFigure.FromGrid(0.69))
    }

    @Test
    fun theGridFigureHoldsWhatTheMainWindowTakesFromTheGrid() {
        val day = day(listOf(Plans.window(amsterdam, today, 13, 15, importKwh = 0.69)))

        assertThat(SourceFigure.of(day).filterIsInstance<SourceFigure.FromGrid>().map { it.kwh }).containsExactly(0.69)
    }

    @Test
    fun aDayWithoutAWindowShowsOnlyItsBattery() {
        val day = day(listOf())

        assertThat(SourceFigure.of(day)).containsExactly(SourceFigure.Battery(BatteryLevels(Percent(40.0), Percent(40.0), Optional.empty())))
    }

    @Test
    fun aWindowThatTakesTooLittleFromTheGridHasNoGridFigure() {
        val day = day(listOf(Plans.window(amsterdam, today, 13, 15, importKwh = 0.049)))

        assertThat(SourceFigure.of(day).map { it.source }).containsExactly(EnergySource.SOLAR, EnergySource.CAR)
    }
}
