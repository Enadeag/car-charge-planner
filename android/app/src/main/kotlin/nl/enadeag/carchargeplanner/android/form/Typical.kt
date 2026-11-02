package nl.enadeag.carchargeplanner.android.form

import nl.enadeag.carchargeplanner.engine.config.TypicalValues
import nl.enadeag.carchargeplanner.engine.setting.ConfigSetting

/**
 * The typical values with one form's system settings as that form marks them; the other forms' marks stay.
 *
 * @param own the settings of the house, the car or the charger that the form shows
 * @param marked those of them that hold a typical value now
 * @return the changed typical values
 */
internal fun TypicalValues.withSystem(own: Set<ConfigSetting>, marked: Set<ConfigSetting>): TypicalValues =
    TypicalValues(system() - own + marked, panels())
