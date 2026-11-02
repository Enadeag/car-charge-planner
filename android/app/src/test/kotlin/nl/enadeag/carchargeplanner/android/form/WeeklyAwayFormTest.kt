package nl.enadeag.carchargeplanner.android.form

import androidx.lifecycle.SavedStateHandle
import java.time.DayOfWeek
import java.time.LocalTime
import nl.enadeag.carchargeplanner.android.afterTheSystemEndedTheApp
import nl.enadeag.carchargeplanner.engine.away.WeeklyAwayPeriod
import nl.enadeag.carchargeplanner.engine.config.WeeklyAwaySchedule
import nl.enadeag.carchargeplanner.engine.setting.InvalidSetting
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class WeeklyAwayFormTest {

    private val handle = SavedStateHandle()
    private val form = WeeklyAwayForm(handle)

    private val thursday = WeeklyAwayPeriod(DayOfWeek.THURSDAY, LocalTime.of(6, 0), LocalTime.of(19, 0))
    private val stored = DRAFT.withAway(WeeklyAwaySchedule(listOf(thursday)))

    @Test
    fun theStoredPeriodsAreShownAndGivenBackUnchanged() {
        form.load(stored)

        val row = form.rowIds.value.single()
        assertThat(form.text(row, WeeklyAwayForm.Field.WEEKDAY)).isEqualTo("THURSDAY")
        assertThat(form.text(row, WeeklyAwayForm.Field.START)).isEqualTo("06:00")
        assertThat(form.read(stored)).isEqualTo(stored)
        assertThat(form.isEdited()).isFalse()
    }

    @Test
    fun anAddedPeriodIsMondayDuringWorkingHoursAndCanBeChanged() {
        form.load(DRAFT)

        val row = form.add()
        form.choose(row, DayOfWeek.FRIDAY)
        form.edit(row, WeeklyAwayForm.Field.END, "16:30")

        assertThat(form.read(DRAFT)!!.away().weekly())
            .containsExactly(WeeklyAwayPeriod(DayOfWeek.FRIDAY, LocalTime.of(8, 0), LocalTime.of(16, 30)))
    }

    @Test
    fun aPeriodThatDoesNotEndAfterItStartsShowsAtItsEndAndATextThatIsNoTimeAtItsField() {
        form.load(stored)
        val row = form.rowIds.value.single()
        form.edit(row, WeeklyAwayForm.Field.END, "05:00")

        assertThat(form.check(stored)).isNull()
        assertThat(form.problems(row).value).containsExactlyEntriesOf(mapOf(
            WeeklyAwayForm.Field.END to FieldProblem.Refused(InvalidSetting.Rule.NOT_AFTER_START)))

        form.edit(row, WeeklyAwayForm.Field.START, "six")
        assertThat(form.check(stored)).isNull()
        assertThat(form.problems(row).value).containsExactlyEntriesOf(mapOf(
            WeeklyAwayForm.Field.START to FieldProblem.NotATime))
    }

    @Test
    fun removingTheLastPeriodLeavesNoneAndCountsAsAnEdit() {
        form.load(stored)

        form.remove(form.rowIds.value.single())

        assertThat(form.read(stored)!!.away()).isEqualTo(WeeklyAwaySchedule.NONE)
        assertThat(form.isEdited()).isTrue()
    }

    @Test
    fun theRowsTheirTextsAndTheChosenWeekdaysSurviveTheSystemEndingTheApp() {
        form.load(stored)
        val added = form.add()
        form.choose(added, DayOfWeek.SATURDAY)
        form.edit(added, WeeklyAwayForm.Field.START, "09:30")
        form.remove(form.rowIds.value.first())

        val again = WeeklyAwayForm(handle.afterTheSystemEndedTheApp())

        assertThat(again.rowIds.value).containsExactly(added)
        assertThat(again.text(added, WeeklyAwayForm.Field.START)).isEqualTo("09:30")
        assertThat(again.isEdited()).isTrue()
        assertThat(again.read(stored)!!.away().weekly())
            .containsExactly(WeeklyAwayPeriod(DayOfWeek.SATURDAY, LocalTime.of(9, 30), LocalTime.of(17, 0)))
    }
}
