package nl.enadeag.carchargeplanner.android.background

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import nl.enadeag.carchargeplanner.engine.ForecastUse

/**
 * The rounds that ask the providers, on WorkManager: the hourly one, with its flex, and the round now, each under a
 * name of its own so a start never schedules a second; only with a network, the one the "Wi-Fi only" switch says,
 * and never waiting for the battery, as a low battery is when a charge notice matters most. WorkManager keeps them
 * over a restart of the phone and an update of the app, in its own database.
 *
 * @param context the application's context
 */
class WorkManagerRounds(private val context: Context) : RoundWork {

    internal companion object {
        /**
         * The name of the hourly round's work. Never changed after a release: WorkManager keeps work on the phone by
         * its name, so work under an old name would be orphaned, running on with nothing that cancels it.
         */
        const val HOURLY = "hourly-round"

        /** The name of the work of the round now; never changed after a release, as [HOURLY]'s. */
        const val ONCE = "round-now"
    }

    private val manager by lazy { WorkManager.getInstance(context) }

    /** Held while the round now is looked at and scheduled, so two saves at once cannot both add one. */
    private val onceLock = Mutex()

    override fun hourly(wifiOnly: Boolean) {
        val request = PeriodicWorkRequest.Builder(RoundWorker::class.java, BackgroundRules.ROUND_PERIOD,
            BackgroundRules.ROUND_FLEX).setConstraints(constraints(wifiOnly)).build()
        manager.enqueueUniquePeriodicWork(HOURLY, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    override suspend fun once(wifiOnly: Boolean): Unit = onceLock.withLock {
        if (waitingOnce().isEmpty()) {
            // appended after one that runs; else the first of a new chain
            manager.enqueueUniqueWork(ONCE, ExistingWorkPolicy.APPEND_OR_REPLACE, onceRequest(wifiOnly).build())
                .await()
        }
    }

    override suspend fun onceWaitsFor(wifiOnly: Boolean): Unit = onceLock.withLock {
        for (waiting in waitingOnce()) {
            val update = manager.updateWork(onceRequest(wifiOnly).setId(waiting.id).build())
            withContext(Dispatchers.IO) { update.get() }
        }
    }

    override fun cancel() {
        manager.cancelUniqueWork(HOURLY)
        manager.cancelUniqueWork(ONCE)
    }

    override suspend fun hourlyScheduled(): Boolean =
        manager.getWorkInfosForUniqueWorkFlow(HOURLY).first().any { !it.state.isFinished }

    /** The round now's work that has not started yet: enqueued, or blocked behind the one that runs. */
    private suspend fun waitingOnce(): List<WorkInfo> = manager.getWorkInfosForUniqueWorkFlow(ONCE).first()
        .filter { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED }

    private fun onceRequest(wifiOnly: Boolean) =
        OneTimeWorkRequest.Builder(RoundWorker::class.java).setConstraints(constraints(wifiOnly))

    private fun constraints(wifiOnly: Boolean) =
        Constraints.Builder().setRequiredNetworkType(BackgroundRules.network(wifiOnly)).build()
}

/**
 * One round that asks the providers, for the hourly work and the round now. It always ends as a success: a provider
 * that failed is the engine's to wait for, so WorkManager tries nothing again before the next hour.
 *
 * @param context the application's context
 * @param params what WorkManager gives the work
 */
class RoundWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        (applicationContext as BackgroundHost).rounds.run(ForecastUse.ASK_PROVIDERS)
        return Result.success()
    }
}
