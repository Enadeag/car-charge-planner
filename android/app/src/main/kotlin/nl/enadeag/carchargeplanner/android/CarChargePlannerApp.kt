package nl.enadeag.carchargeplanner.android

import android.app.Application
import android.os.StrictMode
import androidx.annotation.VisibleForTesting
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import java.time.Clock
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import nl.enadeag.carchargeplanner.android.background.AndroidNoticeAlarm
import nl.enadeag.carchargeplanner.android.background.BackgroundRounds
import nl.enadeag.carchargeplanner.android.background.BackgroundSchedule
import nl.enadeag.carchargeplanner.android.background.NoticeAlarm
import nl.enadeag.carchargeplanner.android.background.ReschedulingAccess
import nl.enadeag.carchargeplanner.android.background.WorkManagerRounds
import nl.enadeag.carchargeplanner.android.notify.ProblemEpisode
import nl.enadeag.carchargeplanner.android.prefs.PhoneSettings
import nl.enadeag.carchargeplanner.android.prefs.PhoneState
import nl.enadeag.carchargeplanner.android.prefs.PhoneStoreFiles
import nl.enadeag.carchargeplanner.android.prefs.phoneStore
import nl.enadeag.carchargeplanner.android.store.EngineAccess
import nl.enadeag.carchargeplanner.android.store.EngineOnDispatcher
import nl.enadeag.carchargeplanner.android.store.Saves
import nl.enadeag.carchargeplanner.android.ui.notify.Notifications
import nl.enadeag.carchargeplanner.android.ui.notify.allowed
import nl.enadeag.carchargeplanner.android.ui.notify.createNoticeChannels

/**
 * The app's composition root, made once per process before any screen: the platform's parts, the one engine on
 * them, which tells the owner through Android's notifications, the notification channels, the phone's own preferences
 * stores, the work in the background, and the app-wide scope that saves run in, so a save outlives the screen that
 * started it. The screens reach the engine only through [engine] and [saves], off the main thread. The engine is built
 * but not started: only the work in the background makes its rounds ([rounds]), which WorkManager, the alarm and the
 * phone's start also start while the app is closed, in a process that Android starts for them without a screen.
 */
open class CarChargePlannerApp : Application() {

    /** The one clock of the engine, the notifications, the alarm and the screens; a test's app gives a fixed one. */
    open val clock: Clock = Clock.systemUTC()

    /** Where saves and other work that must outlive a screen run; it is never cancelled. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** The platform's parts the engine is made on, one set for the process: the files, the keychain, the API keys. */
    lateinit var platform: AndroidPlatform
        private set

    /** The engine's reads and writes, each off the main thread; a test of the activity puts one in memory. */
    lateinit var engine: EngineAccess
        @VisibleForTesting internal set

    /** The saves of set-up and Settings, and the last one's outcome. */
    lateinit var saves: Saves
        @VisibleForTesting internal set

    /** The phone's own settings, which go along to the owner's next phone: the "Wi-Fi only" switch. */
    lateinit var phoneSettings: PhoneSettings
        private set

    /** The notifications' state on this phone, which stays out of a transfer to the next. */
    lateinit var phoneState: PhoneState
        private set

    /** The episodes of the Problems notification, which the dashboard ends and the work in the background starts. */
    lateinit var problems: ProblemEpisode
        private set

    /** The alarm for the next notice; a device test sets it itself. */
    lateinit var alarm: NoticeAlarm
        private set

    /** The rounds of the work in the background, which the worker and the receivers make. */
    lateinit var rounds: BackgroundRounds
        @VisibleForTesting internal set

    /** Sets the work in the background up, as the screen starts and the owner saves a change. */
    lateinit var schedule: BackgroundSchedule
        @VisibleForTesting internal set

    override fun onCreate() {
        super.onCreate()
        platform = beforeThePolicy { AndroidPlatform.of(this, clock) }
        val notifications = Notifications(this, clock)
        createNoticeChannels(this)
        phoneSettings = PhoneSettings(store(PhoneStoreFiles.SETTINGS), scope)
        phoneState = PhoneState(store(PhoneStoreFiles.STATE))
        problems = ProblemEpisode(phoneState, notifications)
        alarm = AndroidNoticeAlarm(this)
        val work = WorkManagerRounds(this)
        val notify = { allowed(this) }
        val oneRoundAtATime = Mutex()
        rounds = BackgroundRounds({ engine }, problems::afterRound, alarm, work, notify, clock, phoneState,
            oneRoundAtATime)
        schedule = BackgroundSchedule({ engine }, work, alarm, phoneSettings.on, oneRoundAtATime, notify)
        engine = ReschedulingAccess(EngineOnDispatcher(AppEngine.build(platform, notifications, clock),
            Dispatchers.IO)) { scope.launch { schedule.refresh() } }
        saves = Saves(engine, scope)
        scope.launch { phoneSettings.on.distinctUntilChanged().drop(1).collect { schedule.wifiSwitched(it) } }
        // only now: the parts above may touch the disk while they are made
        applyMainThreadPolicy(BuildConfig.DEBUG)
    }

    /**
     * The phone's time zone as it is now, which the owner may change while the app runs.
     *
     * @return the system's default zone
     */
    fun zone(): ZoneId = ZoneId.systemDefault()

    /**
     * Makes a part that asks for the app's folders, which Android makes on the first ask, before the main thread's
     * policy is set. The process starts without one; Robolectric makes each test's Application on the same main thread,
     * where the app of an earlier test set it already.
     *
     * @param make what makes the part
     * @return the part
     */
    private fun <T> beforeThePolicy(make: () -> T): T {
        val policy = StrictMode.allowThreadDiskWrites()
        StrictMode.allowThreadDiskReads()
        return try {
            make()
        } finally {
            StrictMode.setThreadPolicy(policy)
        }
    }

    /** One of the phone's preferences stores, in `files/datastore/<name>.preferences_pb`, read off the main thread. */
    private fun store(name: String): DataStore<Preferences> = phoneStore(scope) { preferencesDataStoreFile(name) }

    internal companion object {

        /**
         * Sets the main thread's policy in the debug build only; the release build keeps none.
         *
         * @param debug whether this is the debug build
         * @param set what puts the policy on the calling thread
         */
        fun applyMainThreadPolicy(
            debug: Boolean,
            set: (StrictMode.ThreadPolicy) -> Unit = StrictMode::setThreadPolicy,
        ) {
            if (debug) {
                set(mainThreadPolicy())
            }
        }

        /**
         * What the debug build allows on the main thread: no disk reads, disk writes or network from the app's own
         * code, or the app crashes at once, so a call that should run off the main thread is found while developing.
         * Such a call from the system's or a vendor's code alone is logged as a warning ([MainThreadViolations]).
         *
         * @return a new policy, with a listener of its own
         */
        fun mainThreadPolicy(): StrictMode.ThreadPolicy = StrictMode.ThreadPolicy.Builder()
            .detectDiskReads()
            .detectDiskWrites()
            .detectNetwork()
            .penaltyListener(Runnable::run, MainThreadViolations())
            .build()
    }
}
