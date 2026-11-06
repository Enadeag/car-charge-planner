package nl.enadeag.carchargeplanner.android.background

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import nl.enadeag.carchargeplanner.android.form.DRAFT
import nl.enadeag.carchargeplanner.android.store.FakeEngineAccess
import nl.enadeag.carchargeplanner.android.store.StartState
import nl.enadeag.carchargeplanner.engine.ForecastUse
import nl.enadeag.carchargeplanner.engine.Outlook
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class BackgroundScheduleTest {

    private val access = FakeEngineAccess(StartState.Ready(DRAFT))
    private val background = FakeBackground()
    private val wifiOnly = MutableStateFlow(false)
    private var allowed = true
    private val schedule = BackgroundSchedule({ access }, background.work, background.alarm, wifiOnly) { allowed }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun aStartWithReadySettingsSchedulesTheHourlyRoundAndOneAtOnceOnTheChosenNetwork(wifi: Boolean) = runTest {
        wifiOnly.value = wifi
        val network = if (wifi) "wifi" else "any"

        schedule.refresh()

        assertThat(background.done).containsExactly("hourly $network", "once $network")
    }

    @Test
    fun aStartWithoutASettingsFileCancelsTheWorkAndTheAlarm() = runTest {
        access.settings = StartState.NotSetUp

        schedule.refresh()

        assertThat(background.done).containsExactly("alarm cancelled", "work cancelled")
    }

    @Test
    fun aStartWhileNotificationsAreNotAllowedCancelsTheWorkAndTheAlarm() = runTest {
        allowed = false

        schedule.refresh()

        assertThat(background.done).containsExactly("alarm cancelled", "work cancelled")
    }

    @Test
    fun aStartWithAFileThatCannotBeUsedLeavesTheWorkAsItIs() = runTest {
        access.settings = StartState.Unusable("engine.json", "broken")

        schedule.refresh()

        assertThat(background.done).isEmpty()
    }

    @Test
    fun afterTheStartOfThePhoneOnlyTheRoundAtOnceIsAsked() = runTest {
        wifiOnly.value = true

        schedule.afterBoot()

        assertThat(background.done).containsExactly("once wifi")
    }

    @Test
    fun afterTheStartOfThePhoneNothingIsAskedWithoutReadySettingsOrNotifications() = runTest {
        access.settings = StartState.NotSetUp
        schedule.afterBoot()
        access.settings = StartState.Ready(DRAFT)
        allowed = false
        schedule.afterBoot()

        assertThat(background.done).isEmpty()
    }

    @Test
    fun theWifiSwitchBringsTheScheduledHourlyRoundToItsNetwork() = runTest {
        background.hourlyIsScheduled = true

        schedule.wifiSwitched(true)
        schedule.wifiSwitched(false)

        assertThat(background.done).containsExactly("hourly wifi", "hourly any")
    }

    @Test
    fun theWifiSwitchSchedulesNothingWhileTheHourlyRoundIsNot() = runTest {
        schedule.wifiSwitched(true)

        assertThat(background.done).isEmpty()
    }

    @Test
    fun aFailureIsLoggedAndNotThrown() = runTest {
        access.settings = StartState.NotSetUp
        background.workCancelThrows = IllegalStateException("WorkManager is not initialized")

        schedule.refresh()

        assertThat(background.done).containsExactly("alarm cancelled")
    }

    @Test
    fun aStopWhileTheSettingsAreReadStopsTheRefreshToo() = runTest {
        access.readGate = CompletableDeferred()
        var ended = false

        val refresh = launch {
            schedule.refresh()
            ended = true
        }
        runCurrent()
        refresh.cancel()
        refresh.join()

        assertThat(ended).isFalse()
        assertThat(background.done).isEmpty()
    }

    @Test
    fun aCancelWaitsForTheRoundThatRunsSoItsAlarmDoesNotComeBack() = runTest {
        val now = Instant.parse("2026-06-07T09:00:00Z")
        val due = now.plus(Duration.ofHours(2))
        access.roundOutlook = Outlook.NoForecast("Open-Meteo could not be reached")
        access.noticeDue = due
        val gate = CompletableDeferred<Unit>()
        access.roundGate = gate
        val rounds = BackgroundRounds({ access }, { _, _ -> }, background.alarm, background.work, { true },
            Clock.fixed(now, ZoneOffset.UTC))

        val round = launch { rounds.run(ForecastUse.ASK_PROVIDERS) }
        runCurrent()
        access.settings = StartState.NotSetUp
        val refresh = launch { schedule.refresh() }
        runCurrent()
        assertThat(background.done).isEmpty()

        gate.complete(Unit)
        round.join()
        refresh.join()

        assertThat(background.done).containsExactly("alarm $due", "alarm cancelled", "work cancelled")
    }
}
