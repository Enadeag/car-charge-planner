package nl.enadeag.carchargeplanner.android.ui

import nl.enadeag.carchargeplanner.android.home.EnergySource
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
}
