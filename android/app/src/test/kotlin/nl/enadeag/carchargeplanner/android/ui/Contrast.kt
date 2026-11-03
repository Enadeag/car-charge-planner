package nl.enadeag.carchargeplanner.android.ui

import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * WCAG 2's contrast ratio of two colours in sRGB.
 *
 * @param one a colour, such as a text's
 * @param other the other, such as what the text is drawn on
 * @return from 1 to 21
 */
internal fun contrast(one: Color, other: Color): Double {
    val a = luminance(one)
    val b = luminance(other)
    return (max(a, b) + 0.05) / (min(a, b) + 0.05)
}

private fun luminance(colour: Color): Double {
    fun linear(channel: Float): Double =
        if (channel <= 0.04045) channel / 12.92 else ((channel + 0.055) / 1.055).pow(2.4)
    return 0.2126 * linear(colour.red) + 0.7152 * linear(colour.green) + 0.0722 * linear(colour.blue)
}
