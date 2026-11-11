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
        assertThat(form.time(PlanningForm.Field.CHARGING_START)).isEqualTo(LocalTime.of(7, 0))
        assertThat(form.lead()).isEqualTo(Duration.ofMinutes(15))
        assertThat(form.read(stored)).isEqualTo(stored)
    }

    @Test
    fun typedValuesBecomeTheEnginesUnits() {
        form.load(stored)
        form.edit(PlanningForm.Field.MARGIN, "90")
        form.edit(PlanningForm.Field.MINIMUM_WINDOW, "60")
        form.choose(PlanningForm.Field.CHARGING_END, LocalTime.of(21, 30))
        form.choose(Duration.ZERO)

        val planning = form.read(stored)!!.planning()
        assertThat(planning.margin()).isEqualTo(0.9)
        assertThat(planning.minWindow()).isEqualTo(Duration.ofMinutes(60))
        assertThat(planning.chargingHours().end()).isEqualTo(LocalTime.of(21, 30))
        assertThat(planning.notifyLead()).isZero()
    }

    @Test
    fun aDurationWithDecimalsIsNotAWholeNumberOfMinutes() {
        form.load(stored)
        form.edit(PlanningForm.Field.MINIMUM_WINDOW, "7,5")

        assertThat(form.check(stored)).isNull()
        assertThat(form.problems.value).containsExactlyEntriesOf(mapOf(
            PlanningForm.Field.MINIMUM_WINDOW to FieldProblem.NotAWholeNumber))
    }

    @Test
    fun aStoredLeadThatIsNoneOfTheChoicesIsChosenAsItIsAndKeptExactly() {
        val twenty = stored.withPlanning(PlanningPreferences(Percent(30.0), Percent(80.0), 0.85, 0.2,
            Duration.ofMinutes(45), Duration.ofMinutes(20)))
        form.load(twenty)

        assertThat(form.lead()).isEqualTo(Duration.ofMinutes(20))
        assertThat(form.read(twenty)!!.planning().notifyLead()).isEqualTo(Duration.ofMinutes(20))
        form.choose(Duration.ofMinutes(30))
        assertThat(form.lead()).isEqualTo(Duration.ofMinutes(30))
    }

    @Test
    fun choosingTheTimeOrLeadShownIsNoEdit() {
        form.load(stored)

        form.choose(PlanningForm.Field.CHARGING_START, LocalTime.of(7, 0))
        form.choose(Duration.ofMinutes(15))

        assertThat(form.isEdited()).isFalse()
        form.choose(PlanningForm.Field.CHARGING_START, LocalTime.of(6, 0))
        assertThat(form.isEdited()).isTrue()
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
    fun chargingHoursThatEndWhenTheyStartShowAtTheEndUntilTheEndIsChosenAgain() {
        form.load(stored)
        form.choose(PlanningForm.Field.CHARGING_END, LocalTime.of(7, 0))

        assertThat(form.check(stored)).isNull()
        assertThat(form.problems.value).containsExactlyEntriesOf(mapOf(
            PlanningForm.Field.CHARGING_END to FieldProblem.Refused(InvalidSetting.Rule.SAME_AS_START)))

        form.choose(PlanningForm.Field.CHARGING_END, LocalTime.of(2, 0))
        assertThat(form.problems.value).isEmpty()
        assertThat(form.check(stored)!!.planning().chargingHours())
            .isEqualTo(ChargingHours(LocalTime.of(7, 0), LocalTime.of(2, 0)))
    }

    @Test
    fun aTypedTextAnOlderVersionKeptThatIsNoTimeOrLeadShowsTheStandardHoursAndAsksForALead() {
        form.load(stored)
        form.edit(PlanningForm.Field.CHARGING_START, "7 uur")
        form.edit(PlanningForm.Field.CHARGING_END, "10 pm")
        form.edit(PlanningForm.Field.NOTIFY_LEAD, "soon")

        assertThat(form.time(PlanningForm.Field.CHARGING_START)).isEqualTo(ChargingHours.STANDARD.start())
        assertThat(form.time(PlanningForm.Field.CHARGING_END)).isEqualTo(ChargingHours.STANDARD.end())
        assertThat(form.lead()).isNull()
        assertThat(form.check(stored)).isNull()
        assertThat(form.problems.value).containsExactlyEntriesOf(mapOf(
            PlanningForm.Field.NOTIFY_LEAD to FieldProblem.NotANumber))
    }

    @Test
    fun aKeptNegativeLeadIsNoLead() {
        form.load(stored)
        form.edit(PlanningForm.Field.NOTIFY_LEAD, "-5")

        assertThat(form.lead()).isNull()
    }
}
