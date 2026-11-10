package nl.enadeag.carchargeplanner.android.ui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import nl.enadeag.carchargeplanner.android.ui.home.SourceFigure
import nl.enadeag.carchargeplanner.engine.dashboard.EnergySource

/**
 * The colours that mean an energy source on the dashboard: solar amber, grid blue, the car green.
 * They stay fixed whatever the wallpaper's colours, as their meaning must not change; each source's text is drawn on
 * its own container, so its contrast is the same on every phone, at least 4.5:1 in light and in dark. The colour is
 * never the only sign: the text always names the source.
 */
internal data class SourceColours(val solar: Pair, val grid: Pair, val car: Pair, val chart: Chart) {

    /**
     * @property container what its text is drawn on
     */
    data class Pair(val text: Color, val container: Color)

    /**
     * The solar chart's and the battery bar's colours, all on a ground of their own, so that the lines
     * and marks reach 3:1 and the texts 4.5:1 on it whatever the wallpaper's colours. The see-through fills and the
     * grid are not the marks: each fill has a line of its source's colour at its edge.
     *
     * @property ground what the chart and the bar are drawn on, the whole card
     * @property text the card's heading, the line under the chart, "now" and its line, the battery bar's marks
     * @property muted the axis, the legend's words, the house's line, the marks' words and the empty track's outline
     * @property gridLine the chart's grid and the battery bar's empty track
     * @property solar the solar curve's line and its legend's sign
     * @property solarArea the solar curve's fill, see-through
     * @property car the charge windows' mark and legend sign, and the battery level
     * @property carArea the charge windows' band, see-through
     * @property carAfter the battery level after charging, inside a line of [car]
     * @property carText the battery levels' words
     */
    data class Chart(val ground: Color, val text: Color, val muted: Color, val gridLine: Color, val solar: Color,
        val solarArea: Color, val car: Color, val carArea: Color, val carAfter: Color, val carText: Color) {

        fun of(ink: ChartInk): Color = when (ink) {
            ChartInk.GROUND -> ground
            ChartInk.TEXT -> text
            ChartInk.MUTED -> muted
            ChartInk.GRID_LINE -> gridLine
            ChartInk.SOLAR -> solar
            ChartInk.SOLAR_AREA -> solarArea
            ChartInk.CAR -> car
            ChartInk.CAR_AREA -> carArea
            ChartInk.CAR_AFTER -> carAfter
        }
    }

    fun of(source: EnergySource): Pair = when (source) {
        EnergySource.SOLAR -> solar
        EnergySource.GRID -> grid
        EnergySource.CAR -> car
    }

    fun of(figure: SourceFigure): Pair = of(figure.source)

    companion object {

        /** In light, the text colours on their soft fills. */
        val LIGHT = SourceColours(
            solar = Pair(Color(0xFF8F5A00), Color(0xFFFBE7C2)),
            grid = Pair(Color(0xFF2A62B8), Color(0xFFDBE7FA)),
            car = Pair(Color(0xFF17734A), Color(0xFFD3F0E1)),
            chart = Chart(ground = Color(0xFFFFFFFF), text = Color(0xFF17202B), muted = Color(0xFF566373),
                gridLine = Color(0xFFDDE3EA), solar = Color(0xFFB87300), solarArea = Color(0x38B87300),
                car = Color(0xFF1F8F5C), carArea = Color(0x241F8F5C), carAfter = Color(0xFF8FD1B2),
                carText = Color(0xFF17734A)),
        )

        /** In dark, the text colours on their dark fills. */
        val DARK = SourceColours(
            solar = Pair(Color(0xFFF2B33D), Color(0xFF3A2C10)),
            grid = Pair(Color(0xFF6AA2F5), Color(0xFF17283F)),
            car = Pair(Color(0xFF4CC78C), Color(0xFF13301F)),
            chart = Chart(ground = Color(0xFF161C24), text = Color(0xFFE8EDF3), muted = Color(0xFF9AA7B6),
                gridLine = Color(0xFF2A3441), solar = Color(0xFFF2B33D), solarArea = Color(0x42F2B33D),
                car = Color(0xFF4CC78C), carArea = Color(0x294CC78C), carAfter = Color(0xFF2A6B4C),
                carText = Color(0xFF4CC78C)),
        )

        fun of(dark: Boolean): SourceColours = if (dark) DARK else LIGHT
    }
}

/** The colours of the solar chart's and the battery bar's shapes, by their role; [SourceColours.Chart] has each. */
internal enum class ChartInk {

    /** The cards' ground, also the line between the battery's two levels. */
    GROUND,

    /** "Now" and its line, and the battery bar's marks. */
    TEXT,

    /** The house's line and the empty track's outline. */
    MUTED,

    /** The chart's grid and the battery bar's empty track. */
    GRID_LINE,

    /** The solar curve's line. */
    SOLAR,

    /** The solar curve's fill. */
    SOLAR_AREA,

    /** The charge windows' marks, the battery level and the line around the level after charging. */
    CAR,

    /** The charge windows' bands. */
    CAR_AREA,

    /** The battery level after charging. */
    CAR_AFTER,
}

internal val LocalSourceColours = staticCompositionLocalOf { SourceColours.LIGHT }
