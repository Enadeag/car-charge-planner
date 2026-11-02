package nl.enadeag.carchargeplanner.android.form

import androidx.lifecycle.SavedStateHandle
import java.time.Duration
import java.time.LocalTime
import java.util.Locale
import nl.enadeag.carchargeplanner.engine.config.ChargingHours
import nl.enadeag.carchargeplanner.engine.config.Percent
import nl.enadeag.carchargeplanner.engine.config.PlanningPreferences
import nl.enadeag.carchargeplanner.engine.setting.InvalidSetting
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PlanningFormTest {

    private val form = PlanningForm(SavedStateHandle(), NumberText(Locale.forLanguageTag("nl")))

    /** A margin with more decimals than its field shows. */
    private val stored = DRAFT.withPlanning(PlanningPreferences(Percent(30.0), Percent(80.0), 0.8512, 0.2,
        Duration.ofMinutes(45), Duration.ofMinutes(15), ChargingHours(LocalTime.of(7, 0), LocalTime.of(22, 0))))

    @Test
    fun theMarginIsShownInPercentAndAnUntouchedOneIsTheStoredShareExactly() {
        form.load(stored)

        assertThat(form.text(PlanningForm.Field.MARGIN)).isEqualTo("85")
        assertThat(form.text(PlanningForm.Field.IMPORT_TOLERANCE)).isEqualTo("0,20")
        assertThat(form.text(PlanningForm.Field.CHARGING_START)).isEqualTo("07:00")
        assertThat(form.read(stored)).isEqualTo(stored)
    }

    @Test
    fun typedValuesBecomeTheEnginesUnits() {
        form.load(stored)
        form.edit(PlanningForm.Field.MARGIN, "90")
        form.edit(PlanningForm.Field.MINIMUM_WINDOW, "60")
        form.edit(PlanningForm.Field.CHARGING_END, "21:30")

        val planning = form.read(stored)!!.planning()
        assertThat(planning.margin()).isEqualTo(0.9)
        assertThat(planning.minWindow()).isEqualTo(Duration.ofMinutes(60))
        assertThat(planning.chargingHours().end()).isEqualTo(LocalTime.of(21, 30))
    }

    @Test
    fun aDurationWithDecimalsIsNotAWholeNumberOfMinutes() {
        form.load(stored)
        form.edit(PlanningForm.Field.NOTIFY_LEAD, "7,5")

        assertThat(form.check(stored)).isNull()
        assertThat(form.problems.value).containsExactlyEntriesOf(mapOf(
            PlanningForm.Field.NOTIFY_LEAD to FieldProblem.NotAWholeNumber))
    }

    @Test
    fun aMinimumNotBelowTheTargetShowsAtTheMinimum() {
        form.load(stored)
        form.edit(PlanningForm.Field.MINIMUM_CHARGE, "80")

        assertThat(form.check(stored)).isNull()
        assertThat(form.problems.value).containsExactlyEntriesOf(mapOf(
            PlanningForm.Field.MINIMUM_CHARGE to FieldProblem.Refused(InvalidSetting.Rule.NOT_BELOW_TARGET)))
    }

    @Test
    fun aChargeLevelAboveAHundredPercentIsOutOfRangeAndAMarginOfNoneToo() {
        form.load(stored)
        form.edit(PlanningForm.Field.TARGET_CHARGE, "120")
        form.edit(PlanningForm.Field.MARGIN, "0")

        assertThat(form.check(stored)).isNull()
        assertThat(form.problems.value).containsExactlyEntriesOf(mapOf(
            PlanningForm.Field.TARGET_CHARGE to FieldProblem.Refused(InvalidSetting.Rule.OUT_OF_RANGE)))

        form.edit(PlanningForm.Field.TARGET_CHARGE, "100")
        assertThat(form.check(stored)).isNull()
        assertThat(form.problems.value).containsExactlyEntriesOf(mapOf(
            PlanningForm.Field.MARGIN to FieldProblem.Refused(InvalidSetting.Rule.OUT_OF_RANGE)))
    }

    @Test
    fun chargingHoursThatEndWhenTheyStartShowAtTheEndAndATextThatIsNoTimeAtItsField() {
        form.load(stored)
        form.edit(PlanningForm.Field.CHARGING_END, "07:00")

        assertThat(form.check(stored)).isNull()
        assertThat(form.problems.value).containsExactlyEntriesOf(mapOf(
            PlanningForm.Field.CHARGING_END to FieldProblem.Refused(InvalidSetting.Rule.SAME_AS_START)))

        form.edit(PlanningForm.Field.CHARGING_START, "7 uur")
        assertThat(form.check(stored)).isNull()
        assertThat(form.problems.value).containsExactlyEntriesOf(mapOf(
            PlanningForm.Field.CHARGING_START to FieldProblem.NotATime))
    }
}
