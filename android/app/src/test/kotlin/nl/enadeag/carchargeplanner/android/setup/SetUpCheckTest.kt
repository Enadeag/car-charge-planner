package nl.enadeag.carchargeplanner.android.setup

import java.time.ZoneId
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import nl.enadeag.carchargeplanner.android.store.FakeEngineAccess
import nl.enadeag.carchargeplanner.engine.config.EngineConfig
import nl.enadeag.carchargeplanner.engine.config.ProviderId
import nl.enadeag.carchargeplanner.engine.forecast.ConnectionResult
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlinx.coroutines.ExperimentalCoroutinesApi

@OptIn(ExperimentalCoroutinesApi::class)
class SetUpCheckTest {

    private val draft = EngineConfig.setUpDraft(ZoneId.of("Europe/Amsterdam"), ProviderId("open-meteo"))
    private val access = FakeEngineAccess()

    @Test
    fun aForecastReceivedSaysForHowManyDays() = runTest {
        val check = SetUpCheck(access, backgroundScope)

        check.check(draft)
        runCurrent()
        assertThat(check.state.value).isEqualTo(SetUpCheck.State.Asking)
        access.checks.single().complete(ConnectionResult(true, 7, "Received a forecast for 7 days."))
        runCurrent()

        assertThat(check.state.value).isEqualTo(SetUpCheck.State.Works(7))
    }

    @Test
    fun onlyTheLatestCheckCounts() = runTest {
        val check = SetUpCheck(access, backgroundScope)

        check.check(draft)
        runCurrent()
        check.check(draft)
        runCurrent()
        access.checks[1].complete(ConnectionResult(false, 0, "Open-Meteo did not answer."))
        access.checks[0].complete(ConnectionResult(true, 7, "Received a forecast for 7 days."))
        runCurrent()

        assertThat(check.state.value).isEqualTo(SetUpCheck.State.Failed("Open-Meteo did not answer."))
    }
}
