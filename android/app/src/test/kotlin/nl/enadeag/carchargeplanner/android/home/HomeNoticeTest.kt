package nl.enadeag.carchargeplanner.android.home

import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import nl.enadeag.carchargeplanner.android.store.FakeEngineAccess
import nl.enadeag.carchargeplanner.android.store.SaveOutcome
import nl.enadeag.carchargeplanner.android.store.Saves
import nl.enadeag.carchargeplanner.android.store.StartState
import nl.enadeag.carchargeplanner.engine.config.EngineConfig
import nl.enadeag.carchargeplanner.engine.config.House
import nl.enadeag.carchargeplanner.engine.config.ProviderId
import nl.enadeag.carchargeplanner.engine.secrets.KeyChanges
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeNoticeTest {

    private val config = EngineConfig.setUpDraft(ZoneId.of("Europe/Amsterdam"), ProviderId("open-meteo"))
    private val access = FakeEngineAccess(StartState.Ready(config))
    private val unusable = mutableListOf<StartState.WithoutSettings>()

    private fun withHouse(kw: Double) = { it: EngineConfig ->
        it.withSystem(it.system().withHouseAndCar(House(kw), it.system().car()), it.typical())
    }

    @Test
    fun aFailedSaveNoScreenShowedShowsOnTheHomeUntilClosed() = runTest {
        val saves = Saves(access, backgroundScope)
        access.writeFails = true
        saves.update(KeyChanges.NONE, withHouse(0.8))
        runCurrent()

        val notice = HomeNotice(saves, backgroundScope) { unusable += it }
        runCurrent()

        assertThat(notice.notice.value).isEqualTo(SaveOutcome.Failed("No space left on device"))
        assertThat(saves.lastOutcome.value).isNull()
        notice.close()
        assertThat(notice.notice.value).isNull()
    }

    @Test
    fun anOutcomeThatComesWhileTheHomeIsShownShowsThere() = runTest {
        val saves = Saves(access, backgroundScope)
        val notice = HomeNotice(saves, backgroundScope) { unusable += it }
        runCurrent()

        saves.update(KeyChanges.NONE, withHouse(0.8))
        runCurrent()

        assertThat(notice.notice.value).isEqualTo(SaveOutcome.Saved)
    }

    @Test
    fun aFileASaveFoundOfANewerVersionLeadsToItsScreen() = runTest {
        val saves = Saves(access, backgroundScope)
        access.settings = StartState.NewerVersion("version 99")
        saves.update(KeyChanges.NONE, withHouse(0.8))
        runCurrent()

        val notice = HomeNotice(saves, backgroundScope) { unusable += it }
        runCurrent()

        assertThat(unusable).containsExactly(StartState.NewerVersion("version 99"))
        assertThat(notice.notice.value).isNull()
        assertThat(saves.lastOutcome.value).isNull()
    }
}
