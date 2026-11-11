package nl.enadeag.carchargeplanner.android.form

import androidx.lifecycle.SavedStateHandle
import nl.enadeag.carchargeplanner.engine.config.EngineConfig
import nl.enadeag.carchargeplanner.engine.config.House
import nl.enadeag.carchargeplanner.engine.setting.ConfigSetting
import nl.enadeag.carchargeplanner.engine.setting.InvalidSetting

/**
 * The house's own use of power, which may hold a typical value. The field asks for kWh per day, from 0 to 480; the
 * settings keep the average in kW, which a field the owner did not change gives back exactly.
 *
 * @param handle where the field's text is kept
 * @param numbers how the screen's language writes numbers
 */
class HouseForm(handle: SavedStateHandle, numbers: NumberText) :
    FieldsForm<HouseForm.Field>(FormState(handle, "house", Field.entries), numbers) {

    /** The form's one field. */
    enum class Field {
        /** The energy the house uses on its own, in kWh per day, with one decimal. */
        BASELOAD,
    }

    private companion object {
        const val DECIMALS = 1
        const val HOURS_PER_DAY = 24.0
        const val MOST_KWH_PER_DAY = 480.0
        val OWN = setOf(ConfigSetting.HOUSE_BASELOAD)
    }

    override fun load(config: EngineConfig) {
        state.fill(Field.BASELOAD, perDay(config.system().house().baseloadKw()))
        state.markTypical(if (ConfigSetting.HOUSE_BASELOAD in config.typical().system()) setOf(Field.BASELOAD)
            else emptySet())
    }

    /** Fills in the typical use and marks it, as the owner asked. */
    fun useTypical() {
        state.replace(Field.BASELOAD, perDay(House.TYPICAL.baseloadKw()))
        state.markTypical(setOf(Field.BASELOAD))
    }

    override fun readInto(config: EngineConfig, reader: FieldReader<Field>): EngineConfig? {
        val baseload = reader.number(Field.BASELOAD, config.system().house().baseloadKw(), DECIMALS, HOURS_PER_DAY)
        if (baseload * HOURS_PER_DAY > MOST_KWH_PER_DAY) {
            reader.problems.putIfAbsent(Field.BASELOAD, FieldProblem.Refused(InvalidSetting.Rule.OUT_OF_RANGE))
        }
        val house = reader.records({ Field.BASELOAD }) { House(baseload) } ?: return null
        val marked = if (Field.BASELOAD in state.typical()) OWN else emptySet()
        return config.withSystem(config.system().withHouseAndCar(house, config.system().car()),
            config.typical().withSystem(OWN, marked))
    }

    private fun perDay(kw: Double) = numbers.write(kw * HOURS_PER_DAY, DECIMALS)
}
