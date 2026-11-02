package nl.enadeag.carchargeplanner.android.home

import java.time.LocalDate
import java.time.ZoneId
import nl.enadeag.carchargeplanner.engine.config.Percent
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
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
    fun todayIsTheHomesDayEastOfUtc() {
        val tokyo = ZoneId.of("Asia/Tokyo")
        val morning = Plans.window(tokyo, today, 9, 11)
        // 00:30 on 1 June in Tokyo is 15:30 on 31 May in UTC
        val planned = Plans.planned(today.atTime(0, 30).atZone(tokyo), listOf(listOf(morning)))

        assertThat(TodayPlan.of(planned).windows).isEqualTo(TodayPlan.Windows.Today(listOf(morning)))
    }

    @Test
    fun todayIsTheHomesDayWestOfUtc() {
        val losAngeles = ZoneId.of("America/Los_Angeles")
        val yesterday = today.minusDays(1)
        val late = Plans.window(losAngeles, yesterday, 21, 22)
        // 20:00 on 31 May in Los Angeles is 03:00 on 1 June in UTC
        val planned = Plans.planned(yesterday.atTime(20, 0).atZone(losAngeles), listOf(listOf(late)))

        assertThat(TodayPlan.of(planned).windows).isEqualTo(TodayPlan.Windows.Today(listOf(late)))
    }

    @Test
    fun onTheDayTheClocksGoForwardAWindowAcrossTheChangeIsToday() {
        val amsterdam = ZoneId.of("Europe/Amsterdam")
        val change = LocalDate.of(2026, 3, 29)
        // 02:00 does not exist on this day: the clocks go from 02:00 to 03:00; 00:30 is still 28 March in UTC
        val night = Plans.window(amsterdam, change, 1, 4)
        val planned = Plans.planned(change.atTime(0, 30).atZone(amsterdam), listOf(listOf(night)))

        assertThat(TodayPlan.of(planned).windows).isEqualTo(TodayPlan.Windows.Today(listOf(night)))
    }

    @Test
    fun todayWithoutAWindowIsRefused() {
        assertThatThrownBy { TodayPlan.Windows.Today(listOf()) }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun aBatteryLevelTheOwnerEnteredIsNotShownAsAssumed() {
        val planned = Plans.planned(today.atTime(7, 0).atZone(lisbon), listOf(listOf()), assumed = false)

        assertThat(TodayPlan.of(planned).assumedLevel).isNull()
    }
}
