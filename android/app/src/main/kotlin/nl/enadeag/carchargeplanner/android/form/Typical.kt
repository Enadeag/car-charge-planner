package nl.enadeag.carchargeplanner.android.form

import nl.enadeag.carchargeplanner.engine.config.TypicalValues
import nl.enadeag.carchargeplanner.engine.setting.ConfigSetting

/** The typical values with one form's system settings as that form marks them; the other forms' marks stay. */
internal fun TypicalValues.withSystem(own: Set<ConfigSetting>, marked: Set<ConfigSetting>): TypicalValues =
    TypicalValues(system() - own + marked, panels())
