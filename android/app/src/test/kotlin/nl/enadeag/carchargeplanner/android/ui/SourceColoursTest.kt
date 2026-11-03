package nl.enadeag.carchargeplanner.android.ui

import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class SourceColoursTest {

    /** WCAG 2's relative luminance of a colour in sRGB. */
    private fun luminance(colour: Color): Double {
        fun linear(channel: Float): Double =
            if (channel <= 0.04045) channel / 12.92 else ((channel + 0.055) / 1.055).pow(2.4)
        return 0.2126 * linear(colour.red) + 0.7152 * linear(colour.green) + 0.0722 * linear(colour.blue)
    }

    private fun contrast(one: Color, other: Color): Double {
        val a = luminance(one)
        val b = luminance(other)
        return (max(a, b) + 0.05) / (min(a, b) + 0.05)
    }

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
}
