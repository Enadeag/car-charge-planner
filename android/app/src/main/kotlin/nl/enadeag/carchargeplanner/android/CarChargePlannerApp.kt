package nl.enadeag.carchargeplanner.android

import android.app.Application
import android.os.StrictMode
import androidx.annotation.VisibleForTesting
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import nl.enadeag.carchargeplanner.android.notify.ProblemEpisode
import nl.enadeag.carchargeplanner.android.prefs.PhoneSettings
import nl.enadeag.carchargeplanner.android.prefs.PhoneState
import nl.enadeag.carchargeplanner.android.prefs.PhoneStoreFiles
import nl.enadeag.carchargeplanner.android.store.EngineAccess
import nl.enadeag.carchargeplanner.android.store.EngineOnDispatcher
import nl.enadeag.carchargeplanner.android.store.Saves
import nl.enadeag.carchargeplanner.android.ui.notify.Notifications
import nl.enadeag.carchargeplanner.android.ui.notify.createNoticeChannels

/**
 * The app's composition root, made once per process before any screen: the platform's parts, the one engine on
 * them, which tells the owner through Android's notifications, the notification channels, the phone's own preferences
 * stores and the app-wide scope that saves run in, so a save outlives the screen that started it. The screens reach
 * the engine only through [engine] and [saves], off the main thread. The engine is built but not started: only the
 * work in the background makes its rounds (#362).
 */
class CarChargePlannerApp : Application() {

    /** Where saves and other work that must outlive a screen run; it is never cancelled. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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

    override fun onCreate() {
        super.onCreate()
        val platform = AndroidPlatform.of(this)
        val notifications = Notifications(this)
        createNoticeChannels(this)
        engine = EngineOnDispatcher(AppEngine.build(platform, notifications), Dispatchers.IO)
        saves = Saves(engine, scope)
        phoneSettings = PhoneSettings(store(PhoneStoreFiles.SETTINGS), scope)
        phoneState = PhoneState(store(PhoneStoreFiles.STATE))
        problems = ProblemEpisode(phoneState, notifications)
        // only now: the parts above may touch the disk while they are made
        applyMainThreadPolicy(BuildConfig.DEBUG)
    }

    /**
     * One of the phone's preferences stores, in `files/datastore/<name>.preferences_pb`; one store per file in the
     * process, as DataStore needs. Nothing is read until it is asked, off the main thread.
     */
    private fun store(name: String): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = scope) { preferencesDataStoreFile(name) }

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
