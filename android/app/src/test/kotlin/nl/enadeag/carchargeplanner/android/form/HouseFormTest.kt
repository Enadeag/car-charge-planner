package nl.enadeag.carchargeplanner.android.form

import androidx.lifecycle.SavedStateHandle
import java.util.Locale
import nl.enadeag.carchargeplanner.engine.config.House
import nl.enadeag.carchargeplanner.engine.config.TypicalValues
import nl.enadeag.carchargeplanner.engine.setting.ConfigSetting
import nl.enadeag.carchargeplanner.engine.setting.InvalidSetting
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class HouseFormTest {

    private val form = HouseForm(SavedStateHandle(), NumberText(Locale.ENGLISH))

    @Test
    fun theTypicalUseIsMarkedUntilTheOwnerEntersTheirsAndANegativeOneIsRefused() {
        form.load(DRAFT)
        assertThat(form.text(HouseForm.Field.BASELOAD)).isEqualTo("0.50")
        assertThat(form.typical()).containsExactly(HouseForm.Field.BASELOAD)

        form.edit(HouseForm.Field.BASELOAD, "-0.1")

        assertThat(form.typical()).isEmpty()
        assertThat(form.check(DRAFT)).isNull()
        assertThat(form.problems.value).containsExactlyEntriesOf(mapOf(
            HouseForm.Field.BASELOAD to FieldProblem.Refused(InvalidSetting.Rule.OUT_OF_RANGE)))

        form.edit(HouseForm.Field.BASELOAD, "0.35")
        val read = form.check(DRAFT)!!
        assertThat(read.system().house()).isEqualTo(House(0.35))
        assertThat(read.typical().system()).doesNotContain(ConfigSetting.HOUSE_BASELOAD)
            .contains(ConfigSetting.CAR_BATTERY)
    }

    @Test
    fun askingForTheTypicalUseMarksItAgain() {
        val own = DRAFT.withSystem(DRAFT.system().withHouseAndCar(House(1.234), DRAFT.system().car()),
            TypicalValues.NONE)
        form.load(own)

        form.useTypical()

        val read = form.read(own)!!
        assertThat(read.system().house()).isEqualTo(House.TYPICAL)
        assertThat(read.typical().system()).containsExactly(ConfigSetting.HOUSE_BASELOAD)
    }
}
