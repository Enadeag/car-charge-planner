package nl.enadeag.carchargeplanner.android.ui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import nl.enadeag.carchargeplanner.android.home.EnergySource
import nl.enadeag.carchargeplanner.android.home.SourceFigure

/**
 * The colours that mean an energy source on the dashboard, as on the desktop: solar amber, grid blue, the car green.
 * They stay fixed whatever the wallpaper's colours, as their meaning must not change; each source's text is drawn on
 * its own container, so its contrast is the same on every phone, at least 4.5:1 in light and in dark. The colour is
 * never the only sign: the text always names the source.
 *
 * @property solar solar energy
 * @property grid energy from the grid
 * @property car the car and its battery
 */
internal data class SourceColours(val solar: Pair, val grid: Pair, val car: Pair) {

    /**
     * One source's colours.
     *
     * @property text its text
     * @property container what its text is drawn on
     */
    data class Pair(val text: Color, val container: Color)

    /**
     * One source's colours.
     *
     * @param source the source
     * @return its text and container colours
     */
    fun of(source: EnergySource): Pair = when (source) {
        EnergySource.SOLAR -> solar
        EnergySource.GRID -> grid
        EnergySource.CAR -> car
    }

    /**
     * The colours a figure is drawn in: those of the source it is about.
     *
     * @param figure the figure
     * @return its source's text and container colours
     */
    fun of(figure: SourceFigure): Pair = of(figure.source)

    companion object {

        /** In light, the desktop's text colours on its soft fills. */
        val LIGHT = SourceColours(
            solar = Pair(Color(0xFF8F5A00), Color(0xFFFBE7C2)),
            grid = Pair(Color(0xFF2A62B8), Color(0xFFDBE7FA)),
            car = Pair(Color(0xFF17734A), Color(0xFFD3F0E1)),
        )

        /** In dark, the desktop's dark text colours on its dark fills. */
        val DARK = SourceColours(
            solar = Pair(Color(0xFFF2B33D), Color(0xFF3A2C10)),
            grid = Pair(Color(0xFF6AA2F5), Color(0xFF17283F)),
            car = Pair(Color(0xFF4CC78C), Color(0xFF13301F)),
        )

        /**
         * The colours for the system's light or dark.
         *
         * @param dark whether the system is dark
         * @return [DARK] or [LIGHT]
         */
        fun of(dark: Boolean): SourceColours = if (dark) DARK else LIGHT
    }
}

/** The source colours of the theme around, light or dark; [AppTheme] provides them. */
internal val LocalSourceColours = staticCompositionLocalOf { SourceColours.LIGHT }
