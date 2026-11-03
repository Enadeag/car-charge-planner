package nl.enadeag.carchargeplanner.android.ui

import nl.enadeag.carchargeplanner.android.home.BatteryLevels
import nl.enadeag.carchargeplanner.android.home.EnergySource
import nl.enadeag.carchargeplanner.android.home.SourceFigure
import nl.enadeag.carchargeplanner.engine.config.Percent
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
        assertThat(colours.of(SourceFigure.Battery(BatteryLevels(Percent(50.0), Percent(74.0))))).isEqualTo(colours.car)
        assertThat(colours.of(SourceFigure.FromGrid(0.4))).isEqualTo(colours.grid)
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun theChartsTextsReachFourAndAHalfToOneAndTheirLinesAndMarksThreeToOneOnTheirGround(dark: Boolean) {
        val chart = SourceColours.of(dark).chart
        val texts = mapOf("text" to chart.text, "muted" to chart.muted, "battery levels" to chart.carText)
        val marks = mapOf("solar line" to chart.solar, "window mark" to chart.car, "house line" to chart.muted,
            "now line and battery marks" to chart.text)

        texts.forEach { (name, colour) ->
            assertThat(contrast(colour, chart.ground)).`as`("the contrast of %s in %s", name,
                if (dark) "dark" else "light").isGreaterThanOrEqualTo(4.5)
        }
        marks.forEach { (name, colour) ->
            assertThat(contrast(colour, chart.ground)).`as`("the contrast of %s in %s", name,
                if (dark) "dark" else "light").isGreaterThanOrEqualTo(3.0)
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun theBatteryLevelStandsOutThreeToOneFromTheEmptyTrack(dark: Boolean) {
        val chart = SourceColours.of(dark).chart

        assertThat(contrast(chart.car, chart.gridLine)).isGreaterThanOrEqualTo(3.0)
    }
}
