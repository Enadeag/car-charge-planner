package nl.enadeag.carchargeplanner.android.form

import nl.enadeag.carchargeplanner.engine.config.EngineConfig

/**
 * One part of the settings as a screen or a step shows it, such as the car: it lays what the owner entered over a
 * config, as the engine's records check it.
 */
interface ConfigForm {

    /**
     * Fills the fields with a config's values and marks its typical ones, as the values not edited.
     *
     * @param config the config to show, such as the stored one or set-up's draft
     */
    fun load(config: EngineConfig)

    /**
     * The config with this part as entered, without showing anything: a field the owner did not edit gives back the
     * config's own value exactly.
     *
     * @param config the config to lay the part over, such as the settings file read again at Save
     * @return the changed config; null when a value is refused
     */
    fun read(config: EngineConfig): EngineConfig?

    /**
     * As [read], and shows each refused value's problem at its field, as Next and Save do; the problems shown
     * before go.
     *
     * @param config the config to lay the part over
     * @return the changed config; null when a value is refused
     */
    fun check(config: EngineConfig): EngineConfig?

    /**
     * Whether the owner changed something since the part was loaded.
     *
     * @return true when leaving would throw edits away
     */
    fun isEdited(): Boolean
}
