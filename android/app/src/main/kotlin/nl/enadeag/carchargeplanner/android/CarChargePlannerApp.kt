package nl.enadeag.carchargeplanner.android

import android.app.Application
import android.os.StrictMode
import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import nl.enadeag.carchargeplanner.android.store.EngineAccess
import nl.enadeag.carchargeplanner.android.store.EngineOnDispatcher
import nl.enadeag.carchargeplanner.android.store.Saves

/**
 * The app's composition root, made once per process before any screen: the platform's parts, the one engine on
 * them, and the app-wide scope that saves run in, so a save outlives the screen that started it. The screens reach the
 * engine only through [engine] and [saves], off the main thread. The engine is built but not started (#277).
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

    override fun onCreate() {
        super.onCreate()
        val platform = AndroidPlatform.of(this)
        engine = EngineOnDispatcher(AppEngine.build(platform, QuietNotifier), Dispatchers.IO)
        saves = Saves(engine, scope)
        // only now: the parts above may touch the disk while they are made
        applyMainThreadPolicy(BuildConfig.DEBUG)
    }

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
