package nl.enadeag.carchargeplanner.android.ui

import java.util.Optional
import nl.enadeag.carchargeplanner.android.ui.home.SourceFigure
import nl.enadeag.carchargeplanner.engine.config.Percent
import nl.enadeag.carchargeplanner.engine.dashboard.BatteryLevels
import nl.enadeag.carchargeplanner.engine.dashboard.EnergySource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class SourceColoursTest {

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun everySourcesTextReachesFourAndAHalfToOneOnItsContainer(dark: Boolean) {
        val colours = SourceColours.of(dark)

        for ((source, pair) in mapOf("solar" to colours.solar, "grid" to colours.grid, "car" to colours.car)) {
            assertThat(contrast(pair.text, pair.container)).`as`("the contrast of %s in %s", source,
                if (dark) "dark" else "light").isGreaterThanOrEqualTo(4.5)
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun theSourcesAreTwoByTwoApartInTheirContainers(dark: Boolean) {
        val colours = SourceColours.of(dark)

        assertThat(setOf(colours.solar.container, colours.grid.container, colours.car.container)).hasSize(3)
        assertThat(setOf(colours.solar.text, colours.grid.text, colours.car.text)).hasSize(3)
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun eachSourceHasItsOwnColours(dark: Boolean) {
        val colours = SourceColours.of(dark)

        assertThat(EnergySource.entries.map { colours.of(it) }).containsExactly(colours.solar, colours.grid, colours.car)
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun aFigureIsDrawnInTheColoursOfItsOwnSource(dark: Boolean) {
        val colours = SourceColours.of(dark)

        assertThat(colours.of(SourceFigure.SolarShare(Percent(92.0)))).isEqualTo(colours.solar)
        assertThat(colours.of(SourceFigure.Battery(BatteryLevels(Percent(50.0), Percent(74.0), Optional.empty()))))
            .isEqualTo(colours.car)
        assertThat(colours.of(SourceFigure.FromGrid(0.4))).isEqualTo(colours.grid)
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun eachInkOfTheChartsIsTheColourOfItsRole(dark: Boolean) {
        val chart = SourceColours.of(dark).chart

        assertThat(ChartInk.entries.associateWith(chart::of)).isEqualTo(mapOf(ChartInk.GROUND to chart.ground,
            ChartInk.TEXT to chart.text, ChartInk.MUTED to chart.muted, ChartInk.GRID_LINE to chart.gridLine,
            ChartInk.SOLAR to chart.solar, ChartInk.SOLAR_AREA to chart.solarArea, ChartInk.CAR to chart.car,
            ChartInk.CAR_AREA to chart.carArea, ChartInk.CAR_AFTER to chart.carAfter))
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun everyInkOfTheChartsIsAColourOfItsOwn(dark: Boolean) {
        val chart = SourceColours.of(dark).chart

        // the level after charging, filled in the ground's colour, would look as empty as the track
        assertThat(ChartInk.entries.map(chart::of).toSet()).hasSize(ChartInk.entries.size)
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun theChartsTextsReachFourAndAHalfToOneAndTheirLinesAndMarksThreeToOneOnTheirGround(dark: Boolean) {
        val chart = SourceColours.of(dark).chart
        val texts = mapOf("text" to chart.of(ChartInk.TEXT), "muted" to chart.of(ChartInk.MUTED),
            "battery levels" to chart.carText)
        val marks = mapOf("solar line" to ChartInk.SOLAR, "window mark and battery level" to ChartInk.CAR,
            "baseload line and the track's outline" to ChartInk.MUTED, "now line and battery marks" to ChartInk.TEXT)
        val ground = chart.of(ChartInk.GROUND)

        texts.forEach { (name, colour) ->
            assertThat(contrast(colour, ground)).`as`("the contrast of %s in %s", name,
                if (dark) "dark" else "light").isGreaterThanOrEqualTo(4.5)
        }
        marks.forEach { (name, ink) ->
            assertThat(contrast(chart.of(ink), ground)).`as`("the contrast of %s in %s", name,
                if (dark) "dark" else "light").isGreaterThanOrEqualTo(3.0)
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun theBatteryLevelStandsOutThreeToOneFromTheEmptyTrack(dark: Boolean) {
        val chart = SourceColours.of(dark).chart

        assertThat(contrast(chart.of(ChartInk.CAR), chart.of(ChartInk.GRID_LINE))).isGreaterThanOrEqualTo(3.0)
    }
}
