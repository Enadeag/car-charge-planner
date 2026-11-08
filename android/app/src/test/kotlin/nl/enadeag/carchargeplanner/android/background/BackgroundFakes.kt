package nl.enadeag.carchargeplanner.android.background

import android.content.Context
import android.os.StrictMode
import android.util.Log
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import java.time.Instant
import kotlinx.coroutines.sync.Mutex

/**
 * The alarm, the work and the count of missed rounds in memory; the alarm and the work write what was done to them, in
 * order, to one list.
 */
internal class FakeBackground {

    /** What was done, such as "alarm 2026-06-07T09:09:00Z", "alarm cancelled", "hourly wifi", "once any". */
    val done = mutableListOf<String>()

    /** Whether the hourly round counts as scheduled. */
    var hourlyIsScheduled = false

    /** The lock of the rounds, which the app hands the rounds and the schedule alike. */
    val oneRoundAtATime = Mutex()

    /** When set, a cancel of the work throws it, as WorkManager may. */
    var workCancelThrows: RuntimeException? = null

    val alarm = object : NoticeAlarm {
        override fun set(at: Instant) {
            done += "alarm $at"
        }

        override fun cancel() {
            done += "alarm cancelled"
        }
    }

    val work = object : RoundWork {
        override fun hourly(wifiOnly: Boolean) {
            done += "hourly " + network(wifiOnly)
        }

        override suspend fun once(wifiOnly: Boolean) {
            done += "once " + network(wifiOnly)
        }

        override suspend fun onceWaitsFor(wifiOnly: Boolean) {
            done += "waiting once " + network(wifiOnly)
        }

        override fun cancel() {
            workCancelThrows?.let { throw it }
            done += "work cancelled"
        }

        override suspend fun hourlyScheduled() = hourlyIsScheduled
    }

    /** The rounds in a row that gave no outlook, as last kept. */
    var missedRounds = 0

    val missed = object : MissedRounds {
        override suspend fun missedRounds() = missedRounds

        override suspend fun missedRounds(count: Int) {
            missedRounds = count
        }
    }

    private fun network(wifiOnly: Boolean) = if (wifiOnly) "wifi" else "any"
}

/** Runs a step on the disk on the main thread, which the debug build's StrictMode otherwise refuses. */
internal fun <T> onDisk(step: () -> T): T {
    val policy = StrictMode.allowThreadDiskWrites()
    StrictMode.allowThreadDiskReads()
    return try {
        step()
    } finally {
        StrictMode.setThreadPolicy(policy)
    }
}

/** WorkManager's test driver in place of the app's, which runs its work at once on the calling thread. */
internal fun testWorkManager(context: Context) = onDisk {
    WorkManagerTestInitHelper.initializeTestWorkManager(context,
        Configuration.Builder().setMinimumLoggingLevel(Log.DEBUG).setExecutor(SynchronousExecutor()).build())
}

/**
 * The unique work under a name, read on the main thread.
 *
 * @param context the application's context
 * @param name the work's unique name
 */
internal fun uniqueWork(context: Context, name: String): List<WorkInfo> =
    onDisk { WorkManager.getInstance(context).getWorkInfosForUniqueWork(name).get() }
