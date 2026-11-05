package nl.enadeag.carchargeplanner.android.ui

import kotlin.math.roundToInt

/** A screen that rounds numbers, which `AppArchitectureTest`'s rule on the round must leave alone. */
internal class ScreenThatRoundsANumber {

    /** Rounds through Java's `Math.round`, a method named as the engine's round. */
    fun whole(value: Double): Long = Math.round(value)

    /** Rounds through Kotlin's `roundToInt`. */
    fun percent(value: Double): Int = value.roundToInt()
}
