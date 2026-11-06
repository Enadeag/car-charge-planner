package nl.enadeag.carchargeplanner.android.background

import android.app.Application
import android.os.StrictMode
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.NetworkType
import androidx.work.WorkInfo
import java.time.Duration
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The work that asks the providers, on WorkManager's test driver. */
@RunWith(AndroidJUnit4::class)
class WorkManagerRoundsTest {

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val rounds = WorkManagerRounds(app)

    @Before
    fun useTestWorkManager() {
        testWorkManager(app)
        // WorkManager's test driver writes its database on the calling thread, which is the main thread here
        StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.LAX)
    }

    private fun work(name: String): List<WorkInfo> = uniqueWork(app, name)

    @Test
    fun theHourlyRoundRunsEvery60MinutesWithAQuarterOfFlexUnderItsName() {
        rounds.hourly(wifiOnly = false)

        val hourly = work(WorkManagerRounds.HOURLY).single()
        assertThat(hourly.state).isEqualTo(WorkInfo.State.ENQUEUED)
        assertThat(hourly.periodicityInfo?.repeatIntervalMillis).isEqualTo(Duration.ofMinutes(60).toMillis())
        assertThat(hourly.periodicityInfo?.flexIntervalMillis).isEqualTo(Duration.ofMinutes(15).toMillis())
        assertThat(work(WorkManagerRounds.ONCE)).isEmpty()
    }

    @Test
    fun theRoundsWaitForANetworkAndNeverForTheBattery() {
        rounds.hourly(wifiOnly = false)
        rounds.once(wifiOnly = true)

        for ((name, network) in listOf(WorkManagerRounds.HOURLY to NetworkType.CONNECTED,
            WorkManagerRounds.ONCE to NetworkType.UNMETERED)) {
            val constraints = work(name).single().constraints
            assertThat(constraints.requiredNetworkType).isEqualTo(network)
            assertThat(constraints.requiresBatteryNotLow()).isFalse()
            assertThat(constraints.requiresCharging()).isFalse()
            assertThat(constraints.requiresDeviceIdle()).isFalse()
            assertThat(constraints.requiresStorageNotLow()).isFalse()
        }
    }

    @Test
    fun theHourlyRoundScheduledAgainKeepsItsPeriodAndTakesTheNewNetwork() {
        rounds.hourly(wifiOnly = false)
        val first = work(WorkManagerRounds.HOURLY).single()

        rounds.hourly(wifiOnly = true)

        val now = work(WorkManagerRounds.HOURLY).single()
        assertThat(now.id).isEqualTo(first.id)
        assertThat(now.nextScheduleTimeMillis).isEqualTo(first.nextScheduleTimeMillis)
        assertThat(now.constraints.requiredNetworkType).isEqualTo(NetworkType.UNMETERED)
    }

    @Test
    fun aSecondRoundAtOnceIsNotAddedWhileTheFirstWaits() {
        rounds.once(wifiOnly = false)
        val first = work(WorkManagerRounds.ONCE).single()

        rounds.once(wifiOnly = true)

        val now = work(WorkManagerRounds.ONCE).single()
        assertThat(now.id).isEqualTo(first.id)
        assertThat(now.constraints.requiredNetworkType).isEqualTo(NetworkType.CONNECTED)
    }

    @Test
    fun aCancelEndsBothAndTheHourlyRoundIsNoLongerScheduled() = runBlocking<Unit> {
        rounds.hourly(wifiOnly = false)
        rounds.once(wifiOnly = false)
        assertThat(rounds.hourlyScheduled()).isTrue()

        rounds.cancel()

        assertThat(work(WorkManagerRounds.HOURLY).single().state).isEqualTo(WorkInfo.State.CANCELLED)
        assertThat(work(WorkManagerRounds.ONCE).single().state).isEqualTo(WorkInfo.State.CANCELLED)
        assertThat(rounds.hourlyScheduled()).isFalse()
    }

    @Test
    fun withoutWorkTheHourlyRoundIsNotScheduled() = runBlocking<Unit> {
        assertThat(rounds.hourlyScheduled()).isFalse()
    }
}
