package nl.enadeag.carchargeplanner.android.home

import nl.enadeag.carchargeplanner.android.ui.ScreenOfTheLogicThatKnowsIt

/** Logic that breaks `AppArchitectureTest`'s rules on the screens and on cycles on purpose, with its screen. */
internal class LogicThatKnowsAScreen {

    /** Makes a screen, which logic must not know. */
    fun screen(): ScreenOfTheLogicThatKnowsIt = ScreenOfTheLogicThatKnowsIt(this)
}
