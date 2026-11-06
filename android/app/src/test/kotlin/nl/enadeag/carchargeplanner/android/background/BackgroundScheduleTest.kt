package nl.enadeag.carchargeplanner.android.background

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import nl.enadeag.carchargeplanner.android.form.DRAFT
import nl.enadeag.carchargeplanner.android.store.FakeEngineAccess
import nl.enadeag.carchargeplanner.android.store.StartState
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
}
