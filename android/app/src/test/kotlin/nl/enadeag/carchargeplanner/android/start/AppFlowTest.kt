package nl.enadeag.carchargeplanner.android.start

import androidx.lifecycle.SavedStateHandle
import java.nio.file.Path
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import nl.enadeag.carchargeplanner.android.form.NumberText
import nl.enadeag.carchargeplanner.android.setup.PlaceSearch
import nl.enadeag.carchargeplanner.android.setup.SetUpAgain
import nl.enadeag.carchargeplanner.android.store.FakeEngineAccess
import nl.enadeag.carchargeplanner.android.store.SaveOutcome
import nl.enadeag.carchargeplanner.android.store.Saves
import nl.enadeag.carchargeplanner.android.store.StartState
import nl.enadeag.carchargeplanner.engine.Outlook
import nl.enadeag.carchargeplanner.engine.config.Coordinates
import nl.enadeag.carchargeplanner.engine.config.EngineConfig
import nl.enadeag.carchargeplanner.engine.config.ProviderId
import nl.enadeag.carchargeplanner.engine.forecast.Place
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppFlowTest {

    private val amsterdam = ZoneId.of("Europe/Amsterdam")
    private val config = EngineConfig.setUpDraft(amsterdam, ProviderId("open-meteo"))
    private val utrecht = Place("Utrecht", Coordinates(52.0907, 5.1214), amsterdam, "", "Netherlands", "")

    private fun TestScope.flow(access: FakeEngineAccess, saves: Saves = Saves(access, backgroundScope)) =
        AppFlow(access, saves, backgroundScope, backgroundScope, SavedStateHandle(), NumberText(Locale.ENGLISH),
            { amsterdam }, { "en" }).also { runCurrent() }

    @Test
    fun withoutSettingsTheAppShowsSetUp() = runTest {
        assertThat(flow(FakeEngineAccess(StartState.NotSetUp)).screen.value)
            .isInstanceOf(AppFlow.Screen.SetUp::class.java)
    }

    @Test
    fun withSettingsTheAppShowsTheHome() = runTest {
        assertThat(flow(FakeEngineAccess(StartState.Ready(config))).screen.value)
            .isInstanceOf(AppFlow.Screen.Home::class.java)
    }

    @Test
    fun aFileOfANewerVersionAsksForAnUpdate() = runTest {
        val screen = flow(FakeEngineAccess(StartState.NewerVersion("version 99"))).screen.value

        assertThat(screen).isEqualTo(AppFlow.Screen.NewerVersion("version 99"))
    }

    @Test
    fun anUnusableFileSetAsideStartsSetUp() = runTest {
        val access = FakeEngineAccess(StartState.Unusable("engine.json", "not valid JSON"))
        val flow = flow(access)
        val again = (flow.screen.value as AppFlow.Screen.Unusable).again
        assertThat(again.unusable.details).isEqualTo("not valid JSON")

        again.ask()
        again.confirm()
        runCurrent()

        assertThat(again.state.value).isInstanceOf(SetUpAgain.State.Moved::class.java)
        assertThat(flow.screen.value).isInstanceOf(AppFlow.Screen.SetUp::class.java)
    }

    @Test
    fun finishingSetUpOpensTheHomeAndSetUpNoLongerTakesTheSavesOutcomes() = runTest {
        val access = FakeEngineAccess(StartState.NotSetUp)
        val saves = Saves(access, backgroundScope)
        val flow = flow(access, saves)
        val session = (flow.screen.value as AppFlow.Screen.SetUp).session
        session.next()
        session.choose(PlaceSearch.Result(utrecht, "Utrecht, Netherlands"))
        repeat(5) { session.next() }

        session.finish()
        runCurrent()

        assertThat(flow.screen.value).isInstanceOf(AppFlow.Screen.Home::class.java)
        access.writeFails = true
        saves.finishSetUp(config)
        runCurrent()
        assertThat(saves.lastOutcome.value).isEqualTo(SaveOutcome.Failed("No space left on device"))
    }

    @Test
    fun finishOverAFileOfANewerVersionAsksForAnUpdate() = runTest {
        val access = FakeEngineAccess(StartState.NotSetUp)
        val flow = flow(access)
        val session = (flow.screen.value as AppFlow.Screen.SetUp).session
        session.next()
        session.choose(PlaceSearch.Result(utrecht, "Utrecht, Netherlands"))
        repeat(5) { session.next() }
        access.settings = StartState.NewerVersion("version 99")

        session.finish()
        runCurrent()

        assertThat(flow.screen.value).isEqualTo(AppFlow.Screen.NewerVersion("version 99"))
    }

    @Test
    fun aHomeWhoseSettingsFileWentReadsItAgain() = runTest {
        val access = FakeEngineAccess(StartState.Ready(config))
        val flow = flow(access)
        access.settings = StartState.NotSetUp

        access.outlooks.single().complete(Outlook.NotSetUp(Path.of("engine.json")))
        runCurrent()

        assertThat(flow.screen.value).isInstanceOf(AppFlow.Screen.SetUp::class.java)
    }
}
