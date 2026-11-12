package nl.enadeag.carchargeplanner.android.ui.home

import java.time.LocalDate
import java.time.ZoneId
import java.util.Optional
import nl.enadeag.carchargeplanner.android.home.Plans
import nl.enadeag.carchargeplanner.engine.config.Percent
import nl.enadeag.carchargeplanner.engine.dashboard.BatteryLevels
import nl.enadeag.carchargeplanner.engine.dashboard.DashboardDay
import nl.enadeag.carchargeplanner.engine.dashboard.DayName
import nl.enadeag.carchargeplanner.engine.dashboard.EnergySource
import nl.enadeag.carchargeplanner.engine.dashboard.NoWindowReason
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

    /** A day without a charge window, for a reason. */
    private fun withoutWindow(why: NoWindowReason) = DashboardDay(DayName.Tomorrow(today.plusDays(1)), Optional.empty(),
        listOf(), Optional.of(why), BatteryLevels(Percent(40.0), Percent(40.0), Optional.empty()), Optional.empty(),
        day(listOf()).chart())

    @Test
    fun aDayWithoutAWindowForTheGridAdviceADepartureATripOrAGridChargeThatStartsShowsOnlyItsBattery() {
        val battery = SourceFigure.Battery(BatteryLevels(Percent(40.0), Percent(40.0), Optional.empty()))

        for (why in listOf(NoWindowReason.GRID, NoWindowReason.DEPARTURE, NoWindowReason.AWAY,
            NoWindowReason.GRID_CHARGE_STARTS)) {
            assertThat(SourceFigure.of(withoutWindow(why))).`as`(why.name).containsExactly(battery)
        }
    }

    @Test
    fun aDayThatNeedsNoChargingOrHasTooLittleSolarPowerShowsNoFigure() {
        assertThat(SourceFigure.of(withoutWindow(NoWindowReason.NOT_NEEDED))).isEmpty()
        assertThat(SourceFigure.of(withoutWindow(NoWindowReason.TOO_LITTLE_SOLAR))).isEmpty()
        assertThat(SourceFigure.of(day(listOf()))).isEmpty()
    }

    @Test
    fun aWindowThatTakesTooLittleFromTheGridHasNoGridFigure() {
        val day = day(listOf(Plans.window(amsterdam, today, 13, 15, importKwh = 0.049)))

        assertThat(SourceFigure.of(day).map { it.source }).containsExactly(EnergySource.SOLAR, EnergySource.CAR)
    }
}
