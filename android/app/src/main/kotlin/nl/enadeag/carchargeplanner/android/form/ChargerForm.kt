package nl.enadeag.carchargeplanner.android.form

import androidx.lifecycle.SavedStateHandle
import nl.enadeag.carchargeplanner.engine.config.ChargePowerChoice
import nl.enadeag.carchargeplanner.engine.config.Charger
import nl.enadeag.carchargeplanner.engine.config.EngineConfig
import nl.enadeag.carchargeplanner.engine.config.HomeChargePower
import nl.enadeag.carchargeplanner.engine.config.Phases
import nl.enadeag.carchargeplanner.engine.setting.ConfigSetting

/**
 * The wall charger: the charge power at home, chosen from a few or "Other", and its phases, the lowest and highest
 * current in whole amps, the voltage in V and the efficiency in %. Each value may hold a typical one. The power shows
 * the choice the values stand for, else "Other"; choosing a power sets the phases, the highest current and 230 V.
 *
 * @param handle where the fields' texts and the chosen phases are kept
 * @param numbers how the screen's language writes numbers
 * @param newWhenTypical true in set-up, which shows a loaded charger whose phases and highest current are typical
 *     values with no power chosen yet
 */
class ChargerForm(private val handle: SavedStateHandle, numbers: NumberText,
    private val newWhenTypical: Boolean = false) :
    FieldsForm<ChargerForm.Field>(FormState(handle, "charger", Field.entries), numbers) {

    /**
     * A field of the form.
     *
     * @property setting the engine's name for it; null for the power, which is not stored itself
     */
    enum class Field(val setting: ConfigSetting?) {
        /** The choice the drop-down shows, a [ChargePowerChoice]'s name; empty before a choice in set-up. */
        POWER(null),
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
        val OWN = Field.entries.mapNotNull { it.setting }.toSet()

        /** The voltage as loaded, written exactly, as its field shows it without decimals. */
        const val LOADED_VOLTS = "charger.loadedVolts"

        /** True once a choice put the voltage in: from then on its text is read as written, also once typed in. */
        const val VOLTS_SET = "charger.voltsSet"

        val POWER_VALUES = setOf(Field.PHASES, Field.MAX_CURRENT, Field.VOLTAGE)

        val FILLED_BY_TYPICAL = setOf(Field.MIN_CURRENT, Field.VOLTAGE, Field.EFFICIENCY)
    }

    /**
     * The phases as chosen.
     *
     * @return the phases; null before the form was loaded
     */
    fun phases(): Phases? = Phases.entries.firstOrNull { it.name == state.text(Field.PHASES) }

    /**
     * The charge power the drop-down shows.
     *
     * @return the power the phases, the highest current and the voltage stand for, else [ChargePowerChoice.OTHER],
     *     which a chosen "Other" also shows until one of them changes; null before a choice in set-up, also when the
     *     state kept from before the system ended the app no longer fits
     */
    fun power(): ChargePowerChoice? = ChargePowerChoice.entries.firstOrNull { it.name == state.text(Field.POWER) }

    /**
     * Chooses the phases, as the owner did: their typical mark goes.
     *
     * @param phases the phases the charger charges on
     */
    fun choose(phases: Phases) = edit(Field.PHASES, phases.name)

    /**
     * Chooses a charge power, as the owner did: it sets the phases and the highest current, whose typical marks go,
     * and 230 V; the lowest current and the efficiency stay.
     *
     * @param power the power
     */
    fun choose(power: HomeChargePower) {
        state.edit(Field.PHASES, power.phases().name)
        state.edit(Field.MAX_CURRENT, power.maxAmps().toString())
        if (volts() != HomeChargePower.VOLTS) {
            setVolts(HomeChargePower.VOLTS) { field, text -> state.edit(field, text) }
        }
        state.choose(Field.POWER, ChargePowerChoice.of(power).name)
    }

    /** Chooses "Other", as the owner did: no value changes, and the power shows "Other" until one does. */
    fun chooseOther() = state.choose(Field.POWER, ChargePowerChoice.OTHER.name)

    override fun edit(field: Field, text: String) {
        super.edit(field, text)
        if (field in POWER_VALUES) {
            followValues()
        }
    }

    override fun load(config: EngineConfig) {
        val charger = config.system().charger()
        val typical = config.typical().system()
        show(charger, state::fill)
        handle[LOADED_VOLTS] = charger.volts().toString()
        handle[VOLTS_SET] = false
        val notChosen = newWhenTypical && ConfigSetting.CHARGER_PHASES in typical &&
            ConfigSetting.CHARGER_MAX_CURRENT in typical
        state.fill(Field.POWER, if (notChosen) "" else ChargePowerChoice.of(charger).name)
        state.markTypical(Field.entries.filter { field -> field.setting?.let { it in typical } == true }.toSet())
    }

    /**
     * Fills the lowest current, the voltage and the efficiency with the typical charger's values and marks them, as
     * the owner asked; the phases and the highest current stay as they are.
     */
    fun useTypical() {
        val volts = volts()
        state.replace(Field.MIN_CURRENT, Charger.TYPICAL.minAmps().toString())
        setVolts(Charger.TYPICAL.volts(), state::replace)
        state.replace(Field.EFFICIENCY, numbers.write(Charger.TYPICAL.efficiency() * 100, 0))
        state.markTypical(state.typical() + FILLED_BY_TYPICAL)
        if (volts() != volts) {
            followValues()
        }
    }

    override fun readInto(config: EngineConfig, reader: FieldReader<Field>): EngineConfig? {
        val stored = config.system().charger()
        if (power() == null) {
            reader.problems[Field.POWER] = FieldProblem.NotChosen
        }
        val phases = if (state.isEdited(Field.PHASES)) phases() ?: stored.phases() else stored.phases()
        val minAmps = reader.whole(Field.MIN_CURRENT, stored.minAmps())
        val maxAmps = reader.whole(Field.MAX_CURRENT, stored.maxAmps())
        val volts = reader.number(Field.VOLTAGE, stored.volts(), 0,
            untouchedIsStored = handle.get<Boolean>(VOLTS_SET) != true)
        val efficiency = reader.number(Field.EFFICIENCY, stored.efficiency(), 0, scale = 100.0)
        val charger = reader.records({ setting -> Field.entries.first { it.setting == setting } }) {
            Charger(phases, minAmps, maxAmps, volts, efficiency)
        } ?: return null
        val marked = state.typical().mapNotNull { it.setting }.toSet()
        return config.withSystem(config.system().withCharger(charger), config.typical().withSystem(OWN, marked))
    }

    private fun followValues() {
        if (power() != null) {
            state.fill(Field.POWER, matching().name)
        }
    }

    private fun matching(): ChargePowerChoice {
        val phases = phases() ?: return ChargePowerChoice.OTHER
        val maxAmps = (numbers.readWhole(state.text(Field.MAX_CURRENT).trim()) as? NumberText.WholeNumber.Read)?.value
            ?: return ChargePowerChoice.OTHER
        val volts = volts() ?: return ChargePowerChoice.OTHER
        return ChargePowerChoice.matching(phases, maxAmps, volts)
    }

    /** The voltage as Save would read it: the loaded one exactly while its text is how that is written. */
    private fun volts(): Double? {
        val loaded = handle.get<String>(LOADED_VOLTS)?.toDoubleOrNull()
        val reader = FieldReader(state, numbers)
        val volts = reader.number(Field.VOLTAGE, loaded ?: 0.0, 0,
            untouchedIsStored = loaded != null && handle.get<Boolean>(VOLTS_SET) != true)
        return volts.takeIf { reader.problems.isEmpty() }
    }

    private fun setVolts(volts: Double, put: (Field, String) -> Unit) {
        put(Field.VOLTAGE, numbers.write(volts, 0))
        handle[VOLTS_SET] = true
    }

    private fun show(charger: Charger, put: (Field, String) -> Unit) {
        put(Field.PHASES, charger.phases().name)
        put(Field.MIN_CURRENT, charger.minAmps().toString())
        put(Field.MAX_CURRENT, charger.maxAmps().toString())
        put(Field.VOLTAGE, numbers.write(charger.volts(), 0))
        put(Field.EFFICIENCY, numbers.write(charger.efficiency() * 100, 0))
    }
}
