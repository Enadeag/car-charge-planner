package nl.enadeag.carchargeplanner.android.form

import androidx.lifecycle.SavedStateHandle
import java.time.ZoneId
import java.util.Locale
import nl.enadeag.carchargeplanner.android.afterTheSystemEndedTheApp
import nl.enadeag.carchargeplanner.engine.config.Car
import nl.enadeag.carchargeplanner.engine.config.EngineConfig
import nl.enadeag.carchargeplanner.engine.config.Percent
import nl.enadeag.carchargeplanner.engine.config.ProviderId
import nl.enadeag.carchargeplanner.engine.config.TypicalValues
import nl.enadeag.carchargeplanner.engine.setting.ConfigSetting
import nl.enadeag.carchargeplanner.engine.setting.InvalidSetting
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CarFormTest {

    private val handle = SavedStateHandle()
    private val numbers = NumberText(Locale.forLanguageTag("nl"))
    private val form = CarForm(handle, numbers)

    /** A car whose values have more decimals than the fields show, nothing typical. */
    private val stored = EngineConfig.setUpDraft(ZoneId.of("Europe/Amsterdam"), ProviderId("open-meteo"))
        .let { draft ->
            draft.withSystem(draft.system().withHouseAndCar(draft.system().house(), Car(77.456, 16.24, 42.7,
                Percent(12.5))), TypicalValues.NONE)
        }

    @Test
    fun theFieldsShowTheCarInTheScreensLanguage() {
        form.load(stored)

        assertThat(form.text(CarForm.Field.BATTERY)).isEqualTo("77,5")
        assertThat(form.text(CarForm.Field.CONSUMPTION)).isEqualTo("16,2")
        assertThat(form.text(CarForm.Field.DAILY_DISTANCE)).isEqualTo("43")
        assertThat(form.text(CarForm.Field.TRIP_SURCHARGE)).isEqualTo("13")
    }

    @Test
    fun untouchedFieldsGiveBackTheStoredValuesExactly() {
        form.load(stored)

        assertThat(form.read(stored)).isEqualTo(stored)
    }

    @Test
    fun aFieldTypedBackToHowItWasShownStillGivesTheStoredValue() {
        form.load(stored)
        form.edit(CarForm.Field.BATTERY, "77")
        form.edit(CarForm.Field.BATTERY, "77,5")

        assertThat(form.read(stored)!!.system().car().usableBatteryKwh()).isEqualTo(77.456)
    }

    @Test
    fun aTypedValueIsReadWithEitherDecimalMark() {
        form.load(stored)
        form.edit(CarForm.Field.BATTERY, "64.5")
        form.edit(CarForm.Field.CONSUMPTION, "17,25")

        val car = form.read(stored)!!.system().car()
        assertThat(car.usableBatteryKwh()).isEqualTo(64.5)
        assertThat(car.consumptionKwhPer100km()).isEqualTo(17.25)
        assertThat(car.kmPerDay()).isEqualTo(42.7)
    }

    @Test
    fun eachFieldShowsItsOwnProblemAtCheckAndLosesItWhenEdited() {
        form.load(stored)
        form.edit(CarForm.Field.BATTERY, "lots")
        form.edit(CarForm.Field.DAILY_DISTANCE, "")

        assertThat(form.check(stored)).isNull()
        assertThat(form.problems.value).containsExactlyInAnyOrderEntriesOf(mapOf(
            CarForm.Field.BATTERY to FieldProblem.NotANumber,
            CarForm.Field.DAILY_DISTANCE to FieldProblem.NotANumber,
        ))

        form.edit(CarForm.Field.BATTERY, "60")

        assertThat(form.problems.value).containsOnlyKeys(CarForm.Field.DAILY_DISTANCE)
    }

    @Test
    fun readingShowsNoProblem() {
        form.load(stored)
        form.edit(CarForm.Field.BATTERY, "lots")

        assertThat(form.read(stored)).isNull()
        assertThat(form.problems.value).isEmpty()
    }

    @Test
    fun aValueTheCarRefusesShowsTheEnginesRuleAtItsField() {
        form.load(stored)
        form.edit(CarForm.Field.CONSUMPTION, "0")

        assertThat(form.check(stored)).isNull()
        assertThat(form.problems.value).containsExactlyEntriesOf(mapOf(
            CarForm.Field.CONSUMPTION to FieldProblem.Refused(InvalidSetting.Rule.OUT_OF_RANGE)))
    }

    @Test
    fun aSurchargeOutsideZeroToAHundredPercentIsOutOfRange() {
        form.load(stored)
        form.edit(CarForm.Field.TRIP_SURCHARGE, "101")

        assertThat(form.check(stored)).isNull()
        assertThat(form.problems.value).containsExactlyEntriesOf(mapOf(
            CarForm.Field.TRIP_SURCHARGE to FieldProblem.Refused(InvalidSetting.Rule.OUT_OF_RANGE)))

        form.edit(CarForm.Field.TRIP_SURCHARGE, "100")
        assertThat(form.check(stored)!!.system().car().tripSurcharge()).isEqualTo(Percent(100.0))

        form.edit(CarForm.Field.TRIP_SURCHARGE, "-1")
        assertThat(form.check(stored)).isNull()
        assertThat(form.problems.value).containsExactlyEntriesOf(mapOf(
            CarForm.Field.TRIP_SURCHARGE to FieldProblem.Refused(InvalidSetting.Rule.OUT_OF_RANGE)))

        form.edit(CarForm.Field.TRIP_SURCHARGE, "0")
        assertThat(form.check(stored)!!.system().car().tripSurcharge()).isEqualTo(Percent(0.0))
    }

    @Test
    fun typicalValuesAreMarkedUntilTheOwnerEntersTheirOwn() {
        val draft = EngineConfig.setUpDraft(ZoneId.of("Europe/Amsterdam"), ProviderId("open-meteo"))
        form.load(draft)
        assertThat(form.typical()).containsExactlyInAnyOrder(CarForm.Field.BATTERY, CarForm.Field.CONSUMPTION,
            CarForm.Field.DAILY_DISTANCE)

        form.edit(CarForm.Field.BATTERY, "75")

        assertThat(form.typical()).containsExactlyInAnyOrder(CarForm.Field.CONSUMPTION, CarForm.Field.DAILY_DISTANCE)
        assertThat(form.read(draft)!!.typical().system())
            .doesNotContain(ConfigSetting.CAR_BATTERY)
            .contains(ConfigSetting.CAR_CONSUMPTION, ConfigSetting.CAR_DAILY_DISTANCE, ConfigSetting.HOUSE_BASELOAD)
    }

    @Test
    fun askingForTypicalValuesFillsAndMarksThem() {
        form.load(stored)

        form.useTypical()

        assertThat(form.typical()).hasSize(3)
        val read = form.read(stored)!!
        assertThat(read.system().car()).isEqualTo(Car(Car.TYPICAL.usableBatteryKwh(),
            Car.TYPICAL.consumptionKwhPer100km(), Car.TYPICAL.kmPerDay(), Percent(12.5)))
        assertThat(read.typical().system()).contains(ConfigSetting.CAR_BATTERY, ConfigSetting.CAR_CONSUMPTION,
            ConfigSetting.CAR_DAILY_DISTANCE)
    }

    @Test
    fun theTextsTheEditsAndTheMarksSurviveTheSystemEndingTheApp() {
        val draft = EngineConfig.setUpDraft(ZoneId.of("Europe/Amsterdam"), ProviderId("open-meteo"))
        form.load(draft)
        form.edit(CarForm.Field.BATTERY, "75,5")

        val again = CarForm(handle.afterTheSystemEndedTheApp(), numbers)

        assertThat(again.text(CarForm.Field.BATTERY)).isEqualTo("75,5")
        assertThat(again.typical()).containsExactlyInAnyOrder(CarForm.Field.CONSUMPTION, CarForm.Field.DAILY_DISTANCE)
        assertThat(again.isEdited()).isTrue()
        assertThat(again.read(draft)!!.system().car().usableBatteryKwh()).isEqualTo(75.5)
    }
}
