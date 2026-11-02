package nl.enadeag.carchargeplanner.android.home

import java.nio.file.Path
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import nl.enadeag.carchargeplanner.android.store.FakeEngineAccess
import nl.enadeag.carchargeplanner.engine.Outlook
import nl.enadeag.carchargeplanner.engine.config.ConfigProblem
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeOutlookTest {

    private val access = FakeEngineAccess()
    private val zone = ZoneId.of("Europe/Amsterdam")
    private val planned = Plans.planned(LocalDate.of(2026, 6, 1).atTime(8, 0).atZone(zone), listOf(listOf()))
    private var lost = 0

    @Test
    fun theHomeAsksOnceWhenItOpensAndShowsThePlan() = runTest {
        val home = HomeOutlook(access, backgroundScope) { lost++ }
        runCurrent()
        assertThat(home.state.value).isEqualTo(HomeOutlook.State.Asking)

        access.outlooks.single().complete(planned)
        runCurrent()

        assertThat(home.state.value).isEqualTo(HomeOutlook.State.Planned(TodayPlan.of(planned)))
        assertThat(access.outlooks).hasSize(1)
    }

    @Test
    fun noForecastSaysWhyAndRefreshAsksAgain() = runTest {
        val home = HomeOutlook(access, backgroundScope) { lost++ }
        runCurrent()
        access.outlooks[0].complete(Outlook.NoForecast("Open-Meteo: no network"))
        runCurrent()
        assertThat(home.state.value).isEqualTo(HomeOutlook.State.NoForecast("Open-Meteo: no network"))

        home.refresh()
        runCurrent()
        assertThat(home.state.value).isEqualTo(HomeOutlook.State.Asking)
        access.outlooks[1].complete(planned)
        runCurrent()

        assertThat(home.state.value).isInstanceOf(HomeOutlook.State.Planned::class.java)
    }

    @Test
    fun aSettingsFileThatIsGoneOrUnusableIsHandedOn() = runTest {
        val home = HomeOutlook(access, backgroundScope) { lost++ }
        runCurrent()
        access.outlooks[0].complete(Outlook.NotSetUp(Path.of("engine.json")))
        runCurrent()
        home.refresh()
        runCurrent()
        access.outlooks[1].complete(Outlook.ConfigUnusable(ConfigProblem.Kind.INVALID, Path.of("engine.json"), "bad"))
        runCurrent()

        assertThat(lost).isEqualTo(2)
    }

    @Test
    fun aPlanThatFailsInTheEngineMayBeTriedAgain() = runTest {
        val home = HomeOutlook(access, backgroundScope) { lost++ }
        runCurrent()

        access.outlooks.single().completeExceptionally(IllegalStateException("a fault"))
        runCurrent()

        assertThat(home.state.value).isInstanceOf(HomeOutlook.State.NoForecast::class.java)
        assertThat(lost).isZero()
    }
}
