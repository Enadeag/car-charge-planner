package nl.enadeag.carchargeplanner.android.background

import android.os.StrictMode
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.testing.WorkManagerTestInitHelper
import java.time.Clock
import java.time.Duration
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import nl.enadeag.carchargeplanner.android.CarChargePlannerApp
import nl.enadeag.carchargeplanner.android.store.FakeEngineAccess
import nl.enadeag.carchargeplanner.engine.Outlook
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The work that asks the providers, on WorkManager's test driver. */
@RunWith(AndroidJUnit4::class)
class WorkManagerRoundsTest {

    private val app = ApplicationProvider.getApplicationContext<CarChargePlannerApp>()
    private val rounds = WorkManagerRounds(app)

    /** Holds the round now that runs in its round, until the test ends. */
    private val held = CompletableDeferred<Unit>()

    @Before
    fun useTestWorkManager() {
        testWorkManager(app)
        // WorkManager's test driver writes its database on the calling thread, which is the main thread here
        StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.LAX)
    }

    @After
    fun letTheRunningRoundEnd() {
        held.complete(Unit)
        waitUntil { work(WorkManagerRounds.ONCE).none { it.state == WorkInfo.State.RUNNING } }
    }

    private fun work(name: String): List<WorkInfo> = uniqueWork(app, name)

    private fun once(wifiOnly: Boolean) = runBlocking { rounds.once(wifiOnly) }

    private fun onceWaitsFor(wifiOnly: Boolean) = runBlocking { rounds.onceWaitsFor(wifiOnly) }

    private fun waitUntil(condition: () -> Boolean) {
        val until = System.nanoTime() + Duration.ofSeconds(10).toNanos()
        while (!condition()) {
            check(System.nanoTime() < until) { "Not met within 10 seconds" }
            Thread.sleep(10)
        }
    }

    /** Schedules the round now on any network and lets it start; it stays in its round until the test ends. */
    private fun aRunningRoundNow(): WorkInfo {
        val access = FakeEngineAccess()
        access.roundOutlook = Outlook.NoForecast("Open-Meteo could not be reached")
        access.roundGate = held
        val background = FakeBackground()
        app.rounds = BackgroundRounds({ access }, { _, _ -> }, background.alarm, background.work, { true },
            Clock.systemUTC(), background.missed)
        once(wifiOnly = false)
        val first = work(WorkManagerRounds.ONCE).single()
        requireNotNull(WorkManagerTestInitHelper.getTestDriver(app)).setAllConstraintsMet(first.id)
        waitUntil { work(WorkManagerRounds.ONCE).single().state == WorkInfo.State.RUNNING }
        return first
    }

    private fun states(): List<WorkInfo.State> = work(WorkManagerRounds.ONCE).map { it.state }

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
    fun theWorkIsKeptUnderNamesThatNeverChangeAfterARelease() {
        rounds.hourly(wifiOnly = false)
        once(wifiOnly = false)

        assertThat(uniqueWork(app, "hourly-round")).hasSize(1)
        assertThat(uniqueWork(app, "round-now")).hasSize(1)
    }

    @Test
    fun theRoundsWaitForANetworkAndNeverForTheBattery() {
        rounds.hourly(wifiOnly = false)
        once(wifiOnly = true)

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
        once(wifiOnly = false)
        val first = work(WorkManagerRounds.ONCE).single()

        once(wifiOnly = true)

        val now = work(WorkManagerRounds.ONCE).single()
        assertThat(now.id).isEqualTo(first.id)
        assertThat(now.constraints.requiredNetworkType).isEqualTo(NetworkType.CONNECTED)
    }

    @Test
    fun aRoundAtOnceAskedWhileOneRunsWaitsAfterIt() {
        val running = aRunningRoundNow()

        once(wifiOnly = true)

        val after = work(WorkManagerRounds.ONCE).single { it.id != running.id }
        assertThat(states()).containsExactlyInAnyOrder(WorkInfo.State.RUNNING, WorkInfo.State.BLOCKED)
        assertThat(after.state).isEqualTo(WorkInfo.State.BLOCKED)
        assertThat(after.constraints.requiredNetworkType).isEqualTo(NetworkType.UNMETERED)
    }

    @Test
    fun twoRoundsAtOnceAskedWhileOneRunsLeaveOnlyOneWaitingAfterIt() {
        aRunningRoundNow()

        once(wifiOnly = false)
        once(wifiOnly = false)

        assertThat(states()).containsExactlyInAnyOrder(WorkInfo.State.RUNNING, WorkInfo.State.BLOCKED)
    }

    @Test
    fun aRoundAtOnceAskedAfterTheLastEndedIsScheduledAnew() {
        val ended = aRunningRoundNow()
        held.complete(Unit)
        waitUntil { work(WorkManagerRounds.ONCE).single().state == WorkInfo.State.SUCCEEDED }

        once(wifiOnly = false)

        assertThat(work(WorkManagerRounds.ONCE).filter { it.id != ended.id }.map { it.state })
            .containsExactly(WorkInfo.State.ENQUEUED)
    }

    @Test
    fun theWifiSwitchBringsTheWaitingRoundAtOnceToItsNetwork() {
        once(wifiOnly = false)
        val first = work(WorkManagerRounds.ONCE).single()

        onceWaitsFor(wifiOnly = true)

        val now = work(WorkManagerRounds.ONCE).single()
        assertThat(now.id).isEqualTo(first.id)
        assertThat(now.state).isEqualTo(WorkInfo.State.ENQUEUED)
        assertThat(now.constraints.requiredNetworkType).isEqualTo(NetworkType.UNMETERED)
    }

    @Test
    fun theWifiSwitchLeavesTheRunningRoundAtOnceAloneAndBringsTheOneAfterItToItsNetwork() {
        val running = aRunningRoundNow()
        once(wifiOnly = false)

        onceWaitsFor(wifiOnly = true)

        val now = work(WorkManagerRounds.ONCE)
        assertThat(now.single { it.id == running.id }.constraints.requiredNetworkType)
            .isEqualTo(NetworkType.CONNECTED)
        val after = now.single { it.id != running.id }
        assertThat(after.state).isEqualTo(WorkInfo.State.BLOCKED)
        assertThat(after.constraints.requiredNetworkType).isEqualTo(NetworkType.UNMETERED)
    }

    @Test
    fun theWifiSwitchSchedulesNoRoundAtOnce() {
        onceWaitsFor(wifiOnly = true)

        assertThat(work(WorkManagerRounds.ONCE)).isEmpty()
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
