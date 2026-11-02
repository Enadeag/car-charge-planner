package nl.enadeag.carchargeplanner.android.form

import androidx.lifecycle.SavedStateHandle
import nl.enadeag.carchargeplanner.engine.config.Charger
import nl.enadeag.carchargeplanner.engine.config.EngineConfig
import nl.enadeag.carchargeplanner.engine.config.Phases
import nl.enadeag.carchargeplanner.engine.setting.ConfigSetting

/**
 * The wall charger: its phases, the lowest and highest current in whole amps, the voltage in V and the efficiency in
 * %. Each may hold a typical value.
 *
 * @param handle where the fields' texts and the chosen phases are kept
 * @param numbers how the screen's language writes numbers
 */
class ChargerForm(handle: SavedStateHandle, numbers: NumberText) :
    FieldsForm<ChargerForm.Field>(FormState(handle, "charger", Field.entries), numbers) {

    /**
     * A field of the form.
     *
     * @property setting the engine's name for it
     */
    enum class Field(val setting: ConfigSetting) {
        /** The phases, chosen rather than typed: [Phases.ONE] or [Phases.THREE]. */
        PHASES(ConfigSetting.CHARGER_PHASES),
        /** The lowest current, in whole amps. */
        MIN_CURRENT(ConfigSetting.CHARGER_MIN_CURRENT),
        /** The highest current, in whole amps. */
        MAX_CURRENT(ConfigSetting.CHARGER_MAX_CURRENT),
        /** The voltage per phase, in V. */
        VOLTAGE(ConfigSetting.CHARGER_VOLTAGE),
        /** The share of the energy that reaches the battery, in %. */
        EFFICIENCY(ConfigSetting.CHARGER_EFFICIENCY),
    }

    private companion object {
        val OWN = Field.entries.map { it.setting }.toSet()
    }

    /**
     * The phases as chosen.
     *
     * @return the phases; null before the form was loaded
     */
    fun phases(): Phases? = Phases.entries.firstOrNull { it.name == state.text(Field.PHASES) }

    /**
     * Chooses the phases, as the owner did: their typical mark goes.
     *
     * @param phases the phases the charger charges on
     */
    fun choose(phases: Phases) = state.edit(Field.PHASES, phases.name)

    override fun load(config: EngineConfig) {
        show(config.system().charger(), state::fill)
        state.markTypical(Field.entries.filter { it.setting in config.typical().system() }.toSet())
    }

    /** Fills every field with the typical charger's values and marks them, as the owner asked. */
    fun useTypical() {
        show(Charger.TYPICAL, state::replace)
        state.markTypical(Field.entries.toSet())
    }

    override fun readInto(config: EngineConfig, reader: FieldReader<Field>): EngineConfig? {
        val stored = config.system().charger()
        val phases = if (state.isEdited(Field.PHASES)) phases() ?: stored.phases() else stored.phases()
        val minAmps = reader.whole(Field.MIN_CURRENT, stored.minAmps())
        val maxAmps = reader.whole(Field.MAX_CURRENT, stored.maxAmps())
        val volts = reader.number(Field.VOLTAGE, stored.volts(), 0)
        val efficiency = reader.number(Field.EFFICIENCY, stored.efficiency(), 0, scale = 100.0)
        val charger = reader.records({ setting -> Field.entries.first { it.setting == setting } }) {
            Charger(phases, minAmps, maxAmps, volts, efficiency)
        } ?: return null
        val marked = state.typical().map { it.setting }.toSet()
        return config.withSystem(config.system().withCharger(charger), config.typical().withSystem(OWN, marked))
    }

    private fun show(charger: Charger, put: (Field, String) -> Unit) {
        put(Field.PHASES, charger.phases().name)
        put(Field.MIN_CURRENT, charger.minAmps().toString())
        put(Field.MAX_CURRENT, charger.maxAmps().toString())
        put(Field.VOLTAGE, numbers.write(charger.volts(), 0))
        put(Field.EFFICIENCY, numbers.write(charger.efficiency() * 100, 0))
    }
}
