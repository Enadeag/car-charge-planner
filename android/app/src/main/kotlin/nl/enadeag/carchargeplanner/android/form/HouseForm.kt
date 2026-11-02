package nl.enadeag.carchargeplanner.android.form

import androidx.lifecycle.SavedStateHandle
import nl.enadeag.carchargeplanner.engine.config.EngineConfig
import nl.enadeag.carchargeplanner.engine.config.House
import nl.enadeag.carchargeplanner.engine.setting.ConfigSetting

/**
 * The house's own use of power, in kW, which may hold a typical value.
 *
 * @param handle where the field's text is kept
 * @param numbers how the screen's language writes numbers
 */
class HouseForm(handle: SavedStateHandle, numbers: NumberText) :
    FieldsForm<HouseForm.Field>(FormState(handle, "house", Field.entries), numbers) {

    /** The form's one field. */
    enum class Field {
        /** The power the house uses on its own, in kW, with two decimals. */
        BASELOAD,
    }

    private companion object {
        const val DECIMALS = 2
        val OWN = setOf(ConfigSetting.HOUSE_BASELOAD)
    }

    override fun load(config: EngineConfig) {
        state.fill(Field.BASELOAD, numbers.write(config.system().house().baseloadKw(), DECIMALS))
        state.markTypical(if (ConfigSetting.HOUSE_BASELOAD in config.typical().system()) setOf(Field.BASELOAD)
            else emptySet())
    }

    /** Fills in the typical use and marks it, as the owner asked. */
    fun useTypical() {
        state.replace(Field.BASELOAD, numbers.write(House.TYPICAL.baseloadKw(), DECIMALS))
        state.markTypical(setOf(Field.BASELOAD))
    }

    override fun readInto(config: EngineConfig, reader: FieldReader<Field>): EngineConfig? {
        val baseload = reader.number(Field.BASELOAD, config.system().house().baseloadKw(), DECIMALS)
        val house = reader.records({ Field.BASELOAD }) { House(baseload) } ?: return null
        val marked = if (Field.BASELOAD in state.typical()) OWN else emptySet()
        return config.withSystem(config.system().withHouseAndCar(house, config.system().car()),
            config.typical().withSystem(OWN, marked))
    }
}
