package nl.enadeag.carchargeplanner.android.ui.home

import nl.enadeag.carchargeplanner.engine.config.Percent
import nl.enadeag.carchargeplanner.engine.dashboard.BatteryLevels
import nl.enadeag.carchargeplanner.engine.dashboard.DashboardDay
import nl.enadeag.carchargeplanner.engine.dashboard.EnergySource
import nl.enadeag.carchargeplanner.engine.dashboard.NoWindowReason

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

    companion object {

        /**
         * A day's figures by source, each shown in its source's colours.
         *
         * @param day the day
         * @return the main window's solar share, the battery, and what the main window takes from the grid when the
         *     day shows it; only the battery on a day without a window for the grid advice, a departure or a trip,
         *     and none on a day that needs no charging or has too little solar power, as the Car battery card has
         *     the levels
         */
        fun of(day: DashboardDay): List<SourceFigure> {
            val main = day.main().orElse(null)
            val battery = when (day.noWindow().orElse(null)) {
                null, NoWindowReason.GRID, NoWindowReason.DEPARTURE, NoWindowReason.AWAY -> Battery(day.battery())
                NoWindowReason.NOT_NEEDED, NoWindowReason.TOO_LITTLE_SOLAR -> null
            }
            return listOfNotNull(main?.let { SolarShare(it.window().solarSharePct()) }, battery,
                main?.gridKwh()?.takeIf { it.isPresent }?.let { FromGrid(it.asDouble) })
        }
    }
}
