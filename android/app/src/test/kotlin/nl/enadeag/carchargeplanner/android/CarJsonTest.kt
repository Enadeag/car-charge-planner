package nl.enadeag.carchargeplanner.android

import nl.enadeag.carchargeplanner.engine.config.Car
import nl.enadeag.carchargeplanner.engine.config.Percent
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import tools.jackson.core.JacksonException

class CarJsonTest {

    @Test
    fun aCarReadBackIsTheCarThatWasWritten() {
        val cars = listOf(Car.TYPICAL, Car(77.5, 16.4, 12.0, Percent(20.0)))

        cars.forEach { car -> assertThat(CarJson.read(CarJson.write(car))).isEqualTo(car) }
    }

    @Test
    fun aCarIsWrittenWithTheNamesOfTheSettingsFile() {
        assertThat(CarJson.write(Car.TYPICAL)).isEqualTo(
            """{"usableBatteryKwh":60.0,"consumptionKwhPer100km":18.0,"kmPerDay":40.0,"tripSurchargePct":15.0}""",
        )
    }

    @Test
    fun aCarWithAMissingValueIsRefused() {
        assertThatThrownBy { CarJson.read("""{"usableBatteryKwh": 60}""") }.isInstanceOf(JacksonException::class.java)
    }
}
