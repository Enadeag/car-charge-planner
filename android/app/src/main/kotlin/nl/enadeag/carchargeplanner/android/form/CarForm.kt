package nl.enadeag.carchargeplanner.android.form

import androidx.lifecycle.SavedStateHandle
import nl.enadeag.carchargeplanner.engine.config.Car
import nl.enadeag.carchargeplanner.engine.config.EngineConfig
import nl.enadeag.carchargeplanner.engine.setting.ConfigSetting

/**
 * The car: its battery in kWh, its use per 100 km, its daily distance in km and the trip surcharge in %.
 * The first three may hold typical values.
 *
 * @param handle where the fields' texts are kept
 * @param numbers how the screen's language writes numbers
 */
class CarForm(handle: SavedStateHandle, numbers: NumberText) :
    FieldsForm<CarForm.Field>(FormState(handle, "car", Field.entries), numbers) {

    /**
     * A field of the form.
     *
     * @property setting the engine's name for it; null for one that is never typical
     * @property decimals the decimals it shows
     */
    enum class Field(val setting: ConfigSetting?, val decimals: Int) {
        /** The usable capacity of the battery, in kWh. */
        BATTERY(ConfigSetting.CAR_BATTERY, 1),
        /** The energy used per 100 km, in kWh. */
        CONSUMPTION(ConfigSetting.CAR_CONSUMPTION, 1),
        /** How far the car is driven on a normal day, trips apart, in km. */
        DAILY_DISTANCE(ConfigSetting.CAR_DAILY_DISTANCE, 0),
        /** The share added for a trip's energy, in %. */
        TRIP_SURCHARGE(null, 0),
    }

    private companion object {
        val OWN = Field.entries.mapNotNull { it.setting }.toSet()
    }

    override fun load(config: EngineConfig) {
        show(config.system().car())
        val typical = config.typical().system()
        state.markTypical(Field.entries.filter { field -> field.setting?.let { it in typical } == true }.toSet())
        state.fill(Field.TRIP_SURCHARGE, numbers.write(config.system().car().tripSurcharge().value(), 0))
    }

    /** Fills the battery, the use and the distance with typical values and marks them, as the owner asked. */
    fun useTypical() {
        val typical = Car.TYPICAL
        state.replace(Field.BATTERY, numbers.write(typical.usableBatteryKwh(), Field.BATTERY.decimals))
        state.replace(Field.CONSUMPTION, numbers.write(typical.consumptionKwhPer100km(), Field.CONSUMPTION.decimals))
        state.replace(Field.DAILY_DISTANCE, numbers.write(typical.kmPerDay(), Field.DAILY_DISTANCE.decimals))
        state.markTypical(setOf(Field.BATTERY, Field.CONSUMPTION, Field.DAILY_DISTANCE))
    }

    override fun readInto(config: EngineConfig, reader: FieldReader<Field>): EngineConfig? {
        val stored = config.system().car()
        val battery = reader.number(Field.BATTERY, stored.usableBatteryKwh(), Field.BATTERY.decimals)
        val consumption = reader.number(Field.CONSUMPTION, stored.consumptionKwhPer100km(), Field.CONSUMPTION.decimals)
        val distance = reader.number(Field.DAILY_DISTANCE, stored.kmPerDay(), Field.DAILY_DISTANCE.decimals)
        val surcharge = reader.percent(Field.TRIP_SURCHARGE, stored.tripSurcharge(), Field.TRIP_SURCHARGE.decimals)
        val car = reader.records({ setting -> Field.entries.first { it.setting == setting } }) {
            Car(battery, consumption, distance, surcharge)
        } ?: return null
        val marked = state.typical().mapNotNull { it.setting }.toSet()
        return config.withSystem(config.system().withHouseAndCar(config.system().house(), car),
            config.typical().withSystem(OWN, marked))
    }

    private fun show(car: Car) {
        state.fill(Field.BATTERY, numbers.write(car.usableBatteryKwh(), Field.BATTERY.decimals))
        state.fill(Field.CONSUMPTION, numbers.write(car.consumptionKwhPer100km(), Field.CONSUMPTION.decimals))
        state.fill(Field.DAILY_DISTANCE, numbers.write(car.kmPerDay(), Field.DAILY_DISTANCE.decimals))
    }
}
