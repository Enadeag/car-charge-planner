package nl.enadeag.carchargeplanner.android.ui

import nl.enadeag.carchargeplanner.android.home.LogicThatKnowsAScreen

/**
 * A screen on logic that knows it, which makes a cycle between `ui` and `home` for `AppArchitectureTest`.
 *
 * @property logic the logic the screen shows
 */
internal class ScreenOfTheLogicThatKnowsIt(val logic: LogicThatKnowsAScreen)
