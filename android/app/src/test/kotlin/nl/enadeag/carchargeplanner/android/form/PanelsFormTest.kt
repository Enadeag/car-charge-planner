package nl.enadeag.carchargeplanner.android.form

import androidx.lifecycle.SavedStateHandle
import java.util.Locale
import nl.enadeag.carchargeplanner.android.afterTheSystemEndedTheApp
import nl.enadeag.carchargeplanner.engine.config.PanelArray
import nl.enadeag.carchargeplanner.engine.config.TypicalValues
import nl.enadeag.carchargeplanner.engine.setting.ConfigSetting
import nl.enadeag.carchargeplanner.engine.setting.InvalidSetting
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PanelsFormTest {

    private val handle = SavedStateHandle()
    private val numbers = NumberText(Locale.ENGLISH)
    private val form = PanelsForm(handle, numbers)

    private val east = PanelArray("east", 3.456, 30.0, -90.0, 0.137)
    private val west = PanelArray("west", 2.0, 30.0, 90.0, 0.14)
    private val stored = DRAFT.withSystem(DRAFT.system().withPanels(listOf(east, west)), TypicalValues.NONE)

    @Test
    fun untouchedArraysAreTheStoredOnesExactly() {
        form.load(stored)

        assertThat(form.rowIds.value).hasSize(2)
        assertThat(form.text(form.rowIds.value[0], PanelsForm.Field.LOSSES)).isEqualTo("14")
        assertThat(form.read(stored)).isEqualTo(stored)
    }

    @Test
    fun removingAnArrayKeepsTheOthersExactAndTheLastOneStays() {
        form.load(stored)
        val (first, second) = form.rowIds.value

        assertThat(form.remove(first)).isTrue()
        assertThat(form.remove(second)).isFalse()

        assertThat(form.read(stored)!!.system().panels()).containsExactly(west)
        assertThat(form.isEdited()).isTrue()
    }

    @Test
    fun anAddedArrayHasTheTypicalValuesMarked() {
        form.load(stored)

        val added = form.add()

        val read = form.read(stored)!!
        assertThat(read.system().panels()).containsExactly(east, west, PanelArray.TYPICAL)
        assertThat(read.typical().panels()).containsExactly(setOf(), setOf(), TypicalValues.PANEL_SETTINGS)
        assertThat(form.typical(added)).doesNotContain(PanelsForm.Field.NAME)
    }

    @Test
    fun eachArrayShowsItsOwnProblems() {
        form.load(stored)
        val (first, second) = form.rowIds.value
        form.edit(first, PanelsForm.Field.TILT, "91")
        form.edit(second, PanelsForm.Field.SIZE, "big")

        assertThat(form.check(stored)).isNull()
        assertThat(form.problems(first).value).containsExactlyEntriesOf(mapOf(
            PanelsForm.Field.TILT to FieldProblem.Refused(InvalidSetting.Rule.OUT_OF_RANGE)))
        assertThat(form.problems(second).value).containsExactlyEntriesOf(mapOf(
            PanelsForm.Field.SIZE to FieldProblem.NotANumber))
    }

    @Test
    fun theTypicalMarksAreKeptPerArrayAndGoWhenTheOwnerTypes() {
        form.load(DRAFT)
        val row = form.rowIds.value.single()

        form.edit(row, PanelsForm.Field.SIZE, "6.2")

        val read = form.read(DRAFT)!!
        assertThat(read.system().panels().single().kwp()).isEqualTo(6.2)
        assertThat(read.typical().panels().single())
            .containsExactlyInAnyOrder(ConfigSetting.PANEL_TILT, ConfigSetting.PANEL_DIRECTION,
                ConfigSetting.PANEL_LOSSES)
    }

    @Test
    fun theRowsAndTheirTextsSurviveTheSystemEndingTheApp() {
        form.load(stored)
        form.add()
        form.edit(form.rowIds.value[0], PanelsForm.Field.NAME, "garage")

        val again = PanelsForm(handle.afterTheSystemEndedTheApp(), numbers)

        assertThat(again.rowIds.value).isEqualTo(form.rowIds.value)
        val panels = again.read(stored)!!.system().panels()
        assertThat(panels).hasSize(3)
        assertThat(panels[0]).isEqualTo(PanelArray("garage", 3.456, 30.0, -90.0, 0.137))
    }
}
