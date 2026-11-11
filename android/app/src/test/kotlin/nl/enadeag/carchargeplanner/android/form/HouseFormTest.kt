package nl.enadeag.carchargeplanner.android.form

import androidx.lifecycle.SavedStateHandle
import java.util.Locale
import nl.enadeag.carchargeplanner.engine.config.EngineConfig
import nl.enadeag.carchargeplanner.engine.config.House
import nl.enadeag.carchargeplanner.engine.config.TypicalValues
import nl.enadeag.carchargeplanner.engine.setting.ConfigSetting
import nl.enadeag.carchargeplanner.engine.setting.InvalidSetting
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class HouseFormTest {

    private val form = HouseForm(SavedStateHandle(), NumberText(Locale.ENGLISH))

    private fun withHouse(kw: Double): EngineConfig =
        DRAFT.withSystem(DRAFT.system().withHouseAndCar(House(kw), DRAFT.system().car()), TypicalValues.NONE)

    @Test
    fun theTypicalUseShowsAsTwelveKwhPerDayMarkedUntilTheOwnerEntersTheirs() {
        form.load(DRAFT)
        assertThat(form.text(HouseForm.Field.BASELOAD)).isEqualTo("12.0")
        assertThat(form.typical()).containsExactly(HouseForm.Field.BASELOAD)

        form.edit(HouseForm.Field.BASELOAD, "8.4")

        assertThat(form.typical()).isEmpty()
        val read = form.check(DRAFT)!!
        assertThat(read.system().house().baseloadKw()).isEqualTo(8.4 / 24)
        assertThat(read.typical().system()).doesNotContain(ConfigSetting.HOUSE_BASELOAD)
            .contains(ConfigSetting.CAR_BATTERY)
    }

    @Test
    fun anUntouchedFieldWritesBackTheExactStoredUseThoughItShowsItRounded() {
        val own = withHouse(0.123)
        form.load(own)
        assertThat(form.text(HouseForm.Field.BASELOAD)).isEqualTo("3.0")

        assertThat(form.check(own)!!.system().house()).isEqualTo(House(0.123))

        form.edit(HouseForm.Field.BASELOAD, "3.0")
        assertThat(form.check(own)!!.system().house()).isEqualTo(House(0.123))
    }

    @ParameterizedTest
    @ValueSource(strings = ["0", "480"])
    fun theEndsOfTheRangeAreAccepted(typed: String) {
        form.load(DRAFT)
        form.edit(HouseForm.Field.BASELOAD, typed)

        assertThat(form.check(DRAFT)!!.system().house().baseloadKw()).isEqualTo(typed.toDouble() / 24)
        assertThat(form.problems.value).isEmpty()
    }

    @ParameterizedTest
    @ValueSource(strings = ["-0.1", "480.1"])
    fun aUseJustOutsideTheRangeIsRefusedAsOutOfRange(typed: String) {
        form.load(DRAFT)
        form.edit(HouseForm.Field.BASELOAD, typed)

        assertThat(form.check(DRAFT)).isNull()
        assertThat(form.problems.value).containsExactlyEntriesOf(mapOf(
            HouseForm.Field.BASELOAD to FieldProblem.Refused(InvalidSetting.Rule.OUT_OF_RANGE)))
    }

    @Test
    fun inDutchTheUseIsWrittenAndReadWithADecimalComma() {
        val dutch = HouseForm(SavedStateHandle(), NumberText(Locale.forLanguageTag("nl")))
        dutch.load(withHouse(0.35))
        assertThat(dutch.text(HouseForm.Field.BASELOAD)).isEqualTo("8,4")

        dutch.edit(HouseForm.Field.BASELOAD, "7,2")

        assertThat(dutch.check(DRAFT)!!.system().house().baseloadKw()).isEqualTo(7.2 / 24)
    }

    @Test
    fun askingForTheTypicalUseMarksItAgain() {
        val own = withHouse(1.234)
        form.load(own)

        form.useTypical()

        assertThat(form.text(HouseForm.Field.BASELOAD)).isEqualTo("12.0")
        val read = form.read(own)!!
        assertThat(read.system().house()).isEqualTo(House.TYPICAL)
        assertThat(read.typical().system()).containsExactly(ConfigSetting.HOUSE_BASELOAD)
    }
}
