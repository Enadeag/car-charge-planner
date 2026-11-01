package nl.enadeag.carchargeplanner.android.form

import androidx.lifecycle.SavedStateHandle
import java.util.Locale
import nl.enadeag.carchargeplanner.engine.config.Charger
import nl.enadeag.carchargeplanner.engine.config.Phases
import nl.enadeag.carchargeplanner.engine.config.TypicalValues
import nl.enadeag.carchargeplanner.engine.setting.ConfigSetting
import nl.enadeag.carchargeplanner.engine.setting.InvalidSetting
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class ChargerFormTest {

    private val form = ChargerForm(SavedStateHandle(), NumberText(Locale.ENGLISH))

    /** A charger whose efficiency has more decimals than its field shows. */
    private val stored = DRAFT.withSystem(DRAFT.system().withCharger(Charger(Phases.THREE, 6, 32, 231.4, 0.937)),
        TypicalValues.NONE)

    @Test
    fun theEfficiencyIsShownInPercentAndAnUntouchedOneIsTheStoredShareExactly() {
        form.load(stored)

        assertThat(form.text(ChargerForm.Field.EFFICIENCY)).isEqualTo("94")
        assertThat(form.phases()).isEqualTo(Phases.THREE)
        assertThat(form.read(stored)).isEqualTo(stored)
    }

    @Test
    fun aTypedEfficiencyInPercentIsKeptAsAShare() {
        form.load(stored)
        form.edit(ChargerForm.Field.EFFICIENCY, "85")

        assertThat(form.read(stored)!!.system().charger().efficiency()).isEqualTo(0.85)
    }

    @ParameterizedTest
    @ValueSource(strings = ["16.5", "16,0"])
    fun aCurrentWithDecimalsIsNotAWholeNumberAndIsNotRounded(text: String) {
        form.load(stored)
        form.edit(ChargerForm.Field.MAX_CURRENT, text)

        assertThat(form.check(stored)).isNull()
        assertThat(form.problems.value).containsExactlyEntriesOf(mapOf(
            ChargerForm.Field.MAX_CURRENT to FieldProblem.NotAWholeNumber))
    }

    @Test
    fun aHighestCurrentBelowTheLowestShowsAtTheHighest() {
        form.load(stored)
        form.edit(ChargerForm.Field.MIN_CURRENT, "10")
        form.edit(ChargerForm.Field.MAX_CURRENT, "8")

        assertThat(form.check(stored)).isNull()
        assertThat(form.problems.value).containsExactlyEntriesOf(mapOf(
            ChargerForm.Field.MAX_CURRENT to FieldProblem.Refused(InvalidSetting.Rule.BELOW_MINIMUM_CURRENT)))
    }

    @Test
    fun anEfficiencyAboveAHundredPercentIsOutOfRange() {
        form.load(stored)
        form.edit(ChargerForm.Field.EFFICIENCY, "101")

        assertThat(form.check(stored)).isNull()
        assertThat(form.problems.value).containsExactlyEntriesOf(mapOf(
            ChargerForm.Field.EFFICIENCY to FieldProblem.Refused(InvalidSetting.Rule.OUT_OF_RANGE)))
    }

    @Test
    fun choosingThePhasesTakesTheirTypicalMarkAway() {
        form.load(DRAFT)
        assertThat(form.typical()).hasSize(ChargerForm.Field.entries.size)

        form.choose(Phases.THREE)

        val read = form.read(DRAFT)!!
        assertThat(read.system().charger().phases()).isEqualTo(Phases.THREE)
        assertThat(read.typical().system()).doesNotContain(ConfigSetting.CHARGER_PHASES)
            .contains(ConfigSetting.CHARGER_MIN_CURRENT)
    }

    @Test
    fun askingForTypicalValuesGivesTheTypicalCharger() {
        form.load(stored)

        form.useTypical()

        assertThat(form.read(stored)!!.system().charger()).isEqualTo(Charger.TYPICAL)
        assertThat(form.typical()).hasSize(ChargerForm.Field.entries.size)
    }
}
