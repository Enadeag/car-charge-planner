package nl.enadeag.carchargeplanner.android.background

import android.Manifest
import androidx.test.core.app.ApplicationProvider
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.NetworkType
import androidx.work.WorkInfo
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import nl.enadeag.carchargeplanner.android.CarChargePlannerApp
import nl.enadeag.carchargeplanner.android.Waiting
import nl.enadeag.carchargeplanner.android.MainActivity
import nl.enadeag.carchargeplanner.android.form.DRAFT
import nl.enadeag.carchargeplanner.android.store.BatteryLevelSave
import nl.enadeag.carchargeplanner.android.store.EngineAccess
import nl.enadeag.carchargeplanner.android.store.FakeEngineAccess
import nl.enadeag.carchargeplanner.android.store.Saves
import nl.enadeag.carchargeplanner.android.store.StartState
import nl.enadeag.carchargeplanner.android.ui.AppViewModel
import nl.enadeag.carchargeplanner.engine.config.Percent
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf

/**
 * When the work in the background is set up, cancelled or brought along, as the app is used: the activity's start,
 * the owner's saves and the "Wi-Fi only" switch, on Robolectric with WorkManager's test driver.
 */
@RunWith(AndroidJUnit4::class)
class BackgroundLifecycleTest {

    private val app = ApplicationProvider.getApplicationContext<CarChargePlannerApp>()
    private val access = FakeEngineAccess(StartState.Ready(DRAFT))
    private val background = FakeBackground()
    private var allowed = true

    /** The engine the app made, with its files in Robolectric's folder, before the one in memory takes its place. */
    private lateinit var appsOwnEngine: EngineAccess

    @Before
    fun partsInMemory() {
        appsOwnEngine = app.engine
        testWorkManager(app)
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        app.engine = access
        app.saves = Saves(access, app.scope)
    }

    private fun fakeSchedule() {
        app.schedule = BackgroundSchedule({ access }, background.work, background.alarm, flowOf(false),
            background.oneRoundAtATime) { allowed }
    }

    /**
     * Waits until [done], as the work that a start or a save begins runs in the app's scope, off the main thread; the
     * main thread is left alone, so the screen's endless progress indicator draws no frames meanwhile.
     */
    private fun waitFor(done: () -> Boolean) {
        assertThat(Waiting.waitUntil(condition = done)).isTrue()
    }

    /**
     * The activity made and started, not shown: its start is what sets the work up, and a screen not shown draws
     * nothing while the plan it asks for never comes.
     */
    private fun started() = Robolectric.buildActivity(MainActivity::class.java).create().start()

    private fun work(name: String): List<WorkInfo> = uniqueWork(app, name)

    @Test
    fun theStartOfTheAppWithReadySettingsSchedulesTheHourlyRoundAndOneAtOnce() {
        val activity = started()
        waitFor { work(WorkManagerRounds.HOURLY).isNotEmpty() && work(WorkManagerRounds.ONCE).isNotEmpty() }
        activity.stop().destroy()

        assertThat(work(WorkManagerRounds.HOURLY).single().state).isEqualTo(WorkInfo.State.ENQUEUED)
        assertThat(work(WorkManagerRounds.HOURLY).single().constraints.requiredNetworkType)
            .isEqualTo(NetworkType.CONNECTED)
    }

    @Test
    fun theStartOfTheAppWithoutASettingsFileCancelsTheWork() {
        fakeSchedule()
        access.settings = StartState.NotSetUp

        val activity = started()
        waitFor { background.done.isNotEmpty() }
        activity.stop().destroy()

        assertThat(background.done).containsExactly("alarm cancelled", "work cancelled")
    }

    @Test
    fun whileNotificationsAreNotAllowedNothingIsScheduledAndTheNextStartWithThemSetsItUp() {
        fakeSchedule()
        allowed = false

        val activity = started()
        waitFor { background.done.isNotEmpty() }
        assertThat(background.done).containsExactly("alarm cancelled", "work cancelled")

        allowed = true
        activity.stop().start()
        waitFor { background.done.size == 4 }
        activity.stop().destroy()

        assertThat(background.done).endsWith("hourly any", "once any")
    }

    @Test
    fun allowingNotificationsOnTheDashboardsCardSetsTheWorkUpWithoutANewStart() {
        fakeSchedule()
        allowed = false
        val activity = started()
        waitFor { background.done.isNotEmpty() }

        // Android's question does not stop the activity, so no new start follows the grant
        allowed = true
        ViewModelProvider(activity.get())[AppViewModel::class.java].card.answered(true)
        waitFor { background.done.size == 4 }
        activity.stop().destroy()

        assertThat(background.done).containsExactly("alarm cancelled", "work cancelled", "hourly any", "once any")
    }

    @Test
    fun theAppsOwnEngineTellsTheScheduleOfAChangeTheOwnerSaved() {
        fakeSchedule()

        val saved = runBlocking { appsOwnEngine.saveBatteryLevel(Percent(40.0)) }
        waitFor { background.done.size == 2 }

        assertThat(saved).isEqualTo(BatteryLevelSave.Saved)
        assertThat(background.done).containsExactly("hourly any", "once any")
    }

    @Test
    fun theWifiSwitchReachesTheScheduledHourlyRoundAndAWaitingRoundNow() {
        fakeSchedule()
        background.hourlyIsScheduled = true

        app.phoneSettings.switch(true)
        waitFor { background.done.size >= 2 }

        assertThat(background.done).containsExactly("hourly wifi", "waiting once wifi")
    }
}
