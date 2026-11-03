package nl.enadeag.carchargeplanner.android.home

import java.nio.file.Path
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import nl.enadeag.carchargeplanner.android.store.FakeEngineAccess
import nl.enadeag.carchargeplanner.engine.Outlook
import nl.enadeag.carchargeplanner.engine.away.OneOffAwayPeriod
import nl.enadeag.carchargeplanner.engine.config.ConfigProblem
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

@OptIn(ExperimentalCoroutinesApi::class)
class HomeOutlookTest {

    /** A clock that stands still until a test moves it. */
    private class TestClock(var now: Instant) : Clock() {
        override fun instant(): Instant = now
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?): Clock = this
    }

    private val access = FakeEngineAccess()
    private val zone = ZoneId.of("Europe/Amsterdam")
    private val today = LocalDate.of(2026, 6, 1)
    private val planned = Plans.planned(today.atTime(8, 0).atZone(zone), listOf(listOf()))
    private val clock = TestClock(today.atTime(8, 0).atZone(zone).toInstant())
    private var lost = 0

    private fun TestScope.home(): HomeOutlook = HomeOutlook(access, backgroundScope, clock) { lost++ }.also {
        runCurrent()
    }

    private fun TestScope.answer(index: Int, outlook: Outlook) {
        access.outlooks[index].complete(outlook)
        runCurrent()
    }

    @Test
    fun theHomeAsksOnceWhenItOpensAndShowsTheDashboardWithTheComingAwayPeriods() = runTest {
        val away = OneOffAwayPeriod.on(today.plusDays(1), LocalTime.of(9, 0), LocalTime.of(17, 0), zone)
        access.away = listOf(away)
        val home = home()
        assertThat(home.state.value).isEqualTo(HomeOutlook.State.Asking)

        answer(0, planned)

        assertThat(home.state.value).isEqualTo(HomeOutlook.State.Planned(Dashboard.of(planned, listOf(away))))
        assertThat((home.state.value as HomeOutlook.State.Planned).dashboard.away).hasSize(1)
        assertThat(access.outlooks).hasSize(1)
    }

    @Test
    fun aForecastWithoutDaysIsNoForecastWithItsFooter() = runTest {
        val home = home()

        answer(0, Plans.planned(today.atTime(8, 0).atZone(zone), listOf()))

        assertThat(home.state.value).isEqualTo(HomeOutlook.State.NoDays(Footer.of(planned), zone))
    }

    @Test
    fun noForecastSaysWhyAndTryAgainAsksAfresh() = runTest {
        val home = home()
        answer(0, Outlook.NoForecast("Open-Meteo: no network"))
        assertThat(home.state.value).isEqualTo(HomeOutlook.State.NoForecast("Open-Meteo: no network"))

        home.refresh()
        runCurrent()
        assertThat(home.state.value).isEqualTo(HomeOutlook.State.Asking)
        answer(1, planned)

        assertThat(home.state.value).isInstanceOf(HomeOutlook.State.Planned::class.java)
    }

    @Test
    fun refreshKeepsThePlanInViewWhileTheNewOneIsMade() = runTest {
        val home = home()
        answer(0, planned)
        val shown = home.state.value

        home.refresh()
        runCurrent()
        assertThat(home.state.value).isEqualTo(shown)
        assertThat(home.refreshing.value).isTrue()
        val later = Plans.planned(today.atTime(9, 0).atZone(zone), listOf(listOf()))
        answer(1, later)

        assertThat(home.state.value).isEqualTo(HomeOutlook.State.Planned(Dashboard.of(later, listOf())))
        assertThat(home.refreshing.value).isFalse()
    }

    @Test
    fun aSettingsFileThatIsGoneOrUnusableIsHandedOn() = runTest {
        val home = home()
        answer(0, Outlook.NotSetUp(Path.of("engine.json")))
        home.refresh()
        runCurrent()
        answer(1, Outlook.ConfigUnusable(ConfigProblem.Kind.INVALID, Path.of("engine.json"), "bad"))

        assertThat(lost).isEqualTo(2)
    }

    @Test
    fun aPlanThatFailsInTheEngineMayBeTriedAgain() = runTest {
        val home = home()

        access.outlooks.single().completeExceptionally(IllegalStateException("a fault"))
        runCurrent()

        assertThat(home.state.value).isEqualTo(HomeOutlook.State.NoForecast("The plan could not be made."))
        assertThat(lost).isZero()
    }

    @ParameterizedTest
    @CsvSource("0, false", "900, false", "901, true", "7200, true")
    fun comingBackToTheAppAsksAgainOnlyWhenThePlanShownIsOlderThanAQuarterOfAnHour(seconds: Long, asks: Boolean) {
        runTest {
            val home = home()
            answer(0, planned)

            clock.now = clock.now.plusSeconds(seconds)
            home.returned()
            runCurrent()

            assertThat(access.outlooks).hasSize(if (asks) 2 else 1)
            assertThat(home.state.value).isInstanceOf(HomeOutlook.State.Planned::class.java)
            assertThat(home.refreshing.value).isEqualTo(asks)
        }
    }

    @Test
    fun theAgeCountsFromTheLastAnswerNotTheFirst() = runTest {
        val home = home()
        answer(0, planned)
        clock.now = clock.now.plus(Duration.ofMinutes(10))
        home.refresh()
        runCurrent()
        answer(1, planned)

        clock.now = clock.now.plus(Duration.ofMinutes(10))
        home.returned()
        runCurrent()

        assertThat(access.outlooks).hasSize(2)
    }

    @Test
    fun comingBackToAnOldNoForecastAsksAgain() = runTest {
        val home = home()
        answer(0, Outlook.NoForecast("Open-Meteo: no network"))

        clock.now = clock.now.plus(Duration.ofMinutes(16))
        home.returned()
        runCurrent()

        assertThat(access.outlooks).hasSize(2)
    }

    @Test
    fun comingBackWhileTheFirstPlanIsMadeAsksNothingMore() = runTest {
        val home = home()

        clock.now = clock.now.plus(Duration.ofHours(1))
        home.returned()
        runCurrent()

        assertThat(access.outlooks).hasSize(1)
    }

    @Test
    fun aHomeBackFromSettingsWhereNothingChangedShowsItsPlanWithoutAskingUntilItIsOld() = runTest {
        val first = home()
        answer(0, planned)
        val kept = first.kept()
        assertThat(kept).isEqualTo(HomeOutlook.Kept(first.state.value, clock.now))

        val home = HomeOutlook(access, backgroundScope, clock, MutableStateFlow(3), kept) { lost++ }
        runCurrent()
        assertThat(home.state.value).isEqualTo(first.state.value)
        assertThat(access.outlooks).hasSize(1)

        clock.now = clock.now.plus(Duration.ofMinutes(16))
        home.returned()
        runCurrent()
        assertThat(access.outlooks).hasSize(2)
    }

    @Test
    fun aHomeThatWasStillAskingKeepsNothingAndAsksAgain() = runTest {
        val first = home()
        assertThat(first.kept()).isNull()

        HomeOutlook(access, backgroundScope, clock, MutableStateFlow(0), first.kept()) { lost++ }
        runCurrent()

        assertThat(access.outlooks).hasSize(2)
    }

    @Test
    fun aSettingsSaveThatChangesSomethingWhileTheHomeIsShownAsksAfresh() = runTest {
        val changes = MutableStateFlow(0)
        val home = HomeOutlook(access, backgroundScope, clock, changes) { lost++ }
        runCurrent()
        answer(0, planned)

        changes.value = 1
        runCurrent()

        assertThat(access.outlooks).hasSize(2)
        assertThat(home.state.value).isEqualTo(HomeOutlook.State.Asking)
    }

    @Test
    fun whileTheHomeAsksAgainAfterASettingsChangeItKeepsNothing() = runTest {
        val changes = MutableStateFlow(0)
        val home = HomeOutlook(access, backgroundScope, clock, changes) { lost++ }
        runCurrent()
        answer(0, planned)

        changes.value = 1
        runCurrent()

        assertThat(home.kept()).isNull()
    }

    @Test
    fun askingIsNoAnswerToKeep() {
        assertThatThrownBy { HomeOutlook.Kept(HomeOutlook.State.Asking, clock.now) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun theAwayPeriodsFailingInTheEngineIsAPlanThatMayBeTriedAgain() = runTest {
        access.awayThrows = IllegalStateException("a fault")
        val home = home()

        answer(0, planned)

        assertThat(home.state.value).isEqualTo(HomeOutlook.State.NoForecast("The plan could not be made."))
        assertThat(lost).isZero()
    }

    @Test
    fun aHomeWhoseScreenIsGoneWhileItAsksShowsNoFailure() = runTest {
        val screen = CoroutineScope(backgroundScope.coroutineContext + Job())
        val home = HomeOutlook(access, screen, clock) { lost++ }
        runCurrent()

        screen.cancel()
        runCurrent()

        assertThat(home.state.value).isEqualTo(HomeOutlook.State.Asking)
    }

    @Test
    fun aPullOrRefreshWhileThePlanIsAskedDoesNotStartItOver() = runTest {
        val home = home()

        home.refresh()
        runCurrent()
        assertThat(access.outlooks).hasSize(1)

        answer(0, planned)
        home.refresh()
        home.refresh()
        runCurrent()
        assertThat(access.outlooks).hasSize(2)
    }

    @Test
    fun comingBackLongAfterASettingsChangeWhileItIsAskedAsksNothingMore() = runTest {
        val changes = MutableStateFlow(0)
        val home = HomeOutlook(access, backgroundScope, clock, changes) { lost++ }
        runCurrent()
        answer(0, planned)
        changes.value = 1
        runCurrent()

        clock.now = clock.now.plus(Duration.ofMinutes(16))
        home.returned()
        runCurrent()

        assertThat(access.outlooks).hasSize(2)
        assertThat(access.outlooks[1].isCancelled).isFalse()
    }

    @Test
    fun comingBackLongAfterWhileARefreshRunsAsksNothingMore() = runTest {
        val home = home()
        answer(0, planned)
        home.refresh()
        runCurrent()

        clock.now = clock.now.plus(Duration.ofMinutes(16))
        home.returned()
        runCurrent()

        assertThat(access.outlooks).hasSize(2)
    }

    @Test
    fun planningAgainKeepsThePlanInViewAndShowsTheNewOne() = runTest {
        val home = home()
        answer(0, planned)
        val shown = home.state.value

        home.planAgain()
        runCurrent()
        assertThat(home.state.value).isEqualTo(shown)
        assertThat(home.refreshing.value).isTrue()
        val later = Plans.planned(today.atTime(9, 0).atZone(zone), listOf(listOf()), assumed = false)
        answer(1, later)

        assertThat(home.state.value).isEqualTo(HomeOutlook.State.Planned(Dashboard.of(later, listOf())))
        assertThat(home.refreshing.value).isFalse()
    }

    @Test
    fun planningAgainWhileAPlanIsAskedAsksAnewAndOnlyTheNewAnswerCounts() = runTest {
        val home = home()
        answer(0, planned)
        home.refresh()
        runCurrent()

        home.planAgain()
        runCurrent()

        assertThat(access.outlooks).hasSize(3)
        val shown = home.state.value
        answer(1, Plans.planned(today.atTime(8, 30).atZone(zone), listOf(listOf())))
        assertThat(home.state.value).isEqualTo(shown)
        assertThat(home.refreshing.value).isTrue()
        val later = Plans.planned(today.atTime(9, 0).atZone(zone), listOf(listOf()), assumed = false)
        answer(2, later)
        assertThat(home.state.value).isEqualTo(HomeOutlook.State.Planned(Dashboard.of(later, listOf())))
    }

    @Test
    fun planningAgainWithoutAPlanShownAsksAsAtTheStart() = runTest {
        val home = home()
        answer(0, Outlook.NoForecast("Open-Meteo: no network"))

        home.planAgain()
        runCurrent()

        assertThat(home.state.value).isEqualTo(HomeOutlook.State.Asking)
        assertThat(home.refreshing.value).isFalse()
    }
}
