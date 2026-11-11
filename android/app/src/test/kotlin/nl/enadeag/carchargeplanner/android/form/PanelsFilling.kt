package nl.enadeag.carchargeplanner.android.form

import nl.enadeag.carchargeplanner.engine.config.CompassPoint
import nl.enadeag.carchargeplanner.engine.config.TiltChoice

/** Fills a row as the owner does on a new array: 20 panels facing south, pitched, placed in 2021, the power empty. */
internal fun PanelsForm.fillIn(row: Int = rowIds.value.first()) {
    edit(row, PanelsForm.Field.COUNT, "20")
    choose(row, CompassPoint.SOUTH)
    choose(row, TiltChoice.PITCHED)
    edit(row, PanelsForm.Field.YEAR, "2021")
}
