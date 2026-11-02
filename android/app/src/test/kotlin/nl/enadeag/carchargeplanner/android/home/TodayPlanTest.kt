package nl.enadeag.carchargeplanner.android.home

import java.time.LocalDate
import java.time.ZoneId
import nl.enadeag.carchargeplanner.engine.config.Percent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class TodayPlanTest {

    private val lisbon = ZoneId.of("Europe/Lisbon")
    private val today = LocalDate.of(2026, 6, 1)

    @Test
    fun todaysWindowsShowAlsoTheOneThatEndedAlready() {
        val morning = Plans.window(lisbon, today, 9, 10)
        val afternoon = Plans.window(lisbon, today, 13, 15)
        val planned = Plans.planned(today.atTime(12, 0).atZone(lisbon), listOf(listOf(morning, afternoon)))

        assertThat(TodayPlan.of(planned)).isEqualTo(
            TodayPlan(lisbon, TodayPlan.Windows.Today(listOf(morning, afternoon)), Percent(40.0)))
    }

    @Test
    fun withoutAWindowTodayTheNextOneShows() {
        val tomorrow = Plans.window(lisbon, today.plusDays(1), 11, 14)
        val planned = Plans.planned(today.atTime(7, 0).atZone(lisbon), listOf(listOf(), listOf(tomorrow)))

        assertThat(TodayPlan.of(planned).windows).isEqualTo(TodayPlan.Windows.Next(tomorrow))
    }

    @Test
    fun withoutAnyWindowThereIsNone() {
        val planned = Plans.planned(today.atTime(7, 0).atZone(lisbon), listOf(listOf(), listOf()))

        assertThat(TodayPlan.of(planned).windows).isEqualTo(TodayPlan.Windows.None)
    }

    @Test
    fun todayIsTheHomesDayNotTheInstantsDayElsewhere() {
        val tokyo = ZoneId.of("Asia/Tokyo")
        val late = Plans.window(tokyo, today, 22, 23)
        // 23:30 on 1 June in Tokyo is still 31 May in UTC
        val planned = Plans.planned(today.atTime(23, 30).atZone(tokyo), listOf(listOf(late)))

        assertThat(TodayPlan.of(planned).windows).isEqualTo(TodayPlan.Windows.Today(listOf(late)))
    }

    @Test
    fun aBatteryLevelTheOwnerEnteredIsNotShownAsAssumed() {
        val planned = Plans.planned(today.atTime(7, 0).atZone(lisbon), listOf(listOf()), assumed = false)

        assertThat(TodayPlan.of(planned).assumedLevel).isNull()
    }
}
