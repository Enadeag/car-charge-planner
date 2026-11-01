package nl.enadeag.carchargeplanner.android

import nl.enadeag.carchargeplanner.engine.config.Car
import nl.enadeag.carchargeplanner.engine.config.Percent
import nl.enadeag.carchargeplanner.engine.store.StoredJson

/**
 * A car written to JSON and read back through the engine's own JSON mapper, [StoredJson], in memory. It shows that
 * Jackson runs on the phone; the names are those of the car in the settings file. The settings file itself comes with
 * the app's storage.
 */
internal object CarJson {

    /**
     * Writes a car as JSON on one line.
     *
     * @param car the car to write
     * @return its JSON text
     */
    fun write(car: Car): String = StoredJson.compact(
        StoredJson.`object`()
            .put("usableBatteryKwh", car.usableBatteryKwh())
            .put("consumptionKwhPer100km", car.consumptionKwhPer100km())
            .put("kmPerDay", car.kmPerDay())
            .put("tripSurchargePct", car.tripSurcharge().value()),
    )

    /**
     * Reads a car that [write] wrote.
     *
     * @param text the JSON text
     * @return the car, checked by its record
     * @throws tools.jackson.core.JacksonException when the text is not JSON, or a value is missing or not a number
     * @throws nl.enadeag.carchargeplanner.engine.setting.InvalidSetting when the car refuses a value
     */
    fun read(text: String): Car {
        val node = StoredJson.parse(text)
        return Car(
            node.required("usableBatteryKwh").doubleValue(),
            node.required("consumptionKwhPer100km").doubleValue(),
            node.required("kmPerDay").doubleValue(),
            Percent(node.required("tripSurchargePct").doubleValue()),
        )
    }
}
