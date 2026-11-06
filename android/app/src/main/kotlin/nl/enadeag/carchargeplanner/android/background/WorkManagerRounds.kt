package nl.enadeag.carchargeplanner.android.background

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import nl.enadeag.carchargeplanner.android.CarChargePlannerApp
import nl.enadeag.carchargeplanner.engine.ForecastUse

/**
 * The rounds that ask the providers, on WorkManager: the hourly one, with its flex, and the one at once, each under a
 * name of its own so a start never schedules a second; only with a network, the one the "Wi-Fi only" switch says,
 * and never waiting for the battery, as a low battery is when a charge notice matters most. WorkManager keeps them
 * over a restart of the phone and an update of the app, in its own database.
 *
 * @param context the application's context
 */
class WorkManagerRounds(private val context: Context) : RoundWork {

    internal companion object {
        /** The name of the hourly round's work. */
        const val HOURLY = "hourly-round"

        /** The name of the work of the round at once. */
        const val ONCE = "round-now"
    }

    private val manager by lazy { WorkManager.getInstance(context) }

    override fun hourly(wifiOnly: Boolean) {
        val request = PeriodicWorkRequest.Builder(RoundWorker::class.java, BackgroundRules.ROUND_PERIOD,
            BackgroundRules.ROUND_FLEX).setConstraints(constraints(wifiOnly)).build()
        manager.enqueueUniquePeriodicWork(HOURLY, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    override fun once(wifiOnly: Boolean) {
        val request = OneTimeWorkRequest.Builder(RoundWorker::class.java).setConstraints(constraints(wifiOnly)).build()
        manager.enqueueUniqueWork(ONCE, ExistingWorkPolicy.KEEP, request)
    }

    override fun cancel() {
        manager.cancelUniqueWork(HOURLY)
        manager.cancelUniqueWork(ONCE)
    }

    override suspend fun hourlyScheduled(): Boolean =
        manager.getWorkInfosForUniqueWorkFlow(HOURLY).first().any { !it.state.isFinished }

    private fun constraints(wifiOnly: Boolean) =
        Constraints.Builder().setRequiredNetworkType(BackgroundRules.network(wifiOnly)).build()
}

/**
 * One round that asks the providers, for the hourly work and the one at once. It always ends as a success: a provider
 * that failed is the engine's to wait for, so WorkManager tries nothing again before the next hour.
 *
 * @param context the application's context
 * @param params what WorkManager gives the work
 */
class RoundWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        (applicationContext as CarChargePlannerApp).rounds.run(ForecastUse.ASK_PROVIDERS)
        return Result.success()
    }
}
