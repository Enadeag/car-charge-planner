package nl.enadeag.carchargeplanner.android.background

import androidx.work.NetworkType
import java.time.Duration
import java.time.Instant
import nl.enadeag.carchargeplanner.android.background.BackgroundRules.AtStart
import nl.enadeag.carchargeplanner.android.form.DRAFT
import nl.enadeag.carchargeplanner.android.store.StartState
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class BackgroundRulesTest {

    private val now = Instant.parse("2026-06-07T09:00:00Z")
    private val ready = StartState.Ready(DRAFT)

    @Test
    fun theAlarmGoesWhenTheNextNoticeIsDue() {
        val due = now.plus(Duration.ofHours(2))

        assertThat(BackgroundRules.alarmAt(due, now)).isEqualTo(due)
    }

    @Test
    fun theAlarmNeverGoesWithinNineMinutesFromNow() {
        val earliest = now.plus(Duration.ofMinutes(9))

        assertThat(BackgroundRules.alarmAt(now, now)).isEqualTo(earliest)
        assertThat(BackgroundRules.alarmAt(now.minus(Duration.ofHours(1)), now)).isEqualTo(earliest)
        assertThat(BackgroundRules.alarmAt(earliest.minusMillis(1), now)).isEqualTo(earliest)
        assertThat(BackgroundRules.alarmAt(earliest, now)).isEqualTo(earliest)
        assertThat(BackgroundRules.alarmAt(earliest.plusMillis(1), now)).isEqualTo(earliest.plusMillis(1))
    }

    @Test
    fun withNothingDueThereIsNoAlarm() {
        assertThat(BackgroundRules.alarmAt(null, now)).isNull()
    }

    @Test
    fun readySettingsWithNotificationsAllowedScheduleTheWork() {
        assertThat(BackgroundRules.atStart(ready, allowed = true)).isEqualTo(AtStart.SCHEDULE)
    }

    @Test
    fun withoutASettingsFileOrNotificationsTheWorkIsCancelled() {
        assertThat(BackgroundRules.atStart(StartState.NotSetUp, allowed = true)).isEqualTo(AtStart.CANCEL)
        assertThat(BackgroundRules.atStart(StartState.NotSetUp, allowed = false)).isEqualTo(AtStart.CANCEL)
        assertThat(BackgroundRules.atStart(ready, allowed = false)).isEqualTo(AtStart.CANCEL)
        assertThat(BackgroundRules.atStart(StartState.Unusable("engine.json", "broken"), allowed = false))
            .isEqualTo(AtStart.CANCEL)
    }

    @Test
    fun aFileThatCannotBeUsedLeavesTheWorkAsItIs() {
        assertThat(BackgroundRules.atStart(StartState.Unusable("engine.json", "broken"), allowed = true))
            .isEqualTo(AtStart.LEAVE)
        assertThat(BackgroundRules.atStart(StartState.NewerVersion("version 2"), allowed = true))
            .isEqualTo(AtStart.LEAVE)
    }

    @Test
    fun wifiOnlyWaitsForANetworkWithoutADataLimitAndOtherwiseAnyNetwork() {
        assertThat(BackgroundRules.network(wifiOnly = true)).isEqualTo(NetworkType.UNMETERED)
        assertThat(BackgroundRules.network(wifiOnly = false)).isEqualTo(NetworkType.CONNECTED)
    }

    @Test
    fun theRoundAskingTheProvidersRunsHourlyInItsLastQuarter() {
        assertThat(BackgroundRules.ROUND_PERIOD).isEqualTo(Duration.ofMinutes(60))
        assertThat(BackgroundRules.ROUND_FLEX).isEqualTo(Duration.ofMinutes(15))
    }
}
