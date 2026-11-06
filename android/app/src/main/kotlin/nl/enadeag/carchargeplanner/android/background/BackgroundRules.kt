package nl.enadeag.carchargeplanner.android.background

import androidx.work.NetworkType
import java.time.Duration
import java.time.Instant
import nl.enadeag.carchargeplanner.android.store.StartState

/**
 * The rules of the work in the background, apart from Android: when the alarm goes, what a start does to the schedule,
 * and what the hourly round waits for.
 */
object BackgroundRules {

    /**
     * The least time from now to the next alarm: Android lets an app's allow-while-idle alarms fire at most once per
     * nine minutes, and an alarm that keeps setting itself for "now" would otherwise wake the phone over and over.
     */
    val MIN_ALARM_GAP: Duration = Duration.ofMinutes(9)

    /** How often the round that asks the providers runs while the app is closed. */
    val ROUND_PERIOD: Duration = Duration.ofMinutes(60)

    /** The last part of each period in which that round may run, as Android sees fit. */
    val ROUND_FLEX: Duration = Duration.ofMinutes(15)

    /** What a start of the app, Finish or a change the owner saved does to the work in the background. */
    enum class AtStart {

        /** The hourly round, and one round at once, are scheduled. */
        SCHEDULE,

        /** The work and the alarm are cancelled. */
        CANCEL,

        /** Nothing new is scheduled, and what is scheduled stays. */
        LEAVE,
    }

    /**
     * When the next alarm goes off.
     *
     * @param due when the next notice is due, as the engine says; null when none is to come
     * @param now the moment the alarm is set
     * @return [due], but never earlier than [MIN_ALARM_GAP] after [now]; null when no alarm is to be set
     */
    fun alarmAt(due: Instant?, now: Instant): Instant? {
        if (due == null) {
            return null
        }
        val earliest = now.plus(MIN_ALARM_GAP)
        return if (due.isBefore(earliest)) earliest else due
    }

    /**
     * What a start does to the schedule.
     *
     * @param settings the settings file as read
     * @param allowed whether notifications are allowed now
     * @return [AtStart.CANCEL] without a settings file or while notifications are not allowed;
     *     [AtStart.SCHEDULE] with ready settings; [AtStart.LEAVE] for a file that cannot be used or of a newer version
     */
    fun atStart(settings: StartState, allowed: Boolean): AtStart = when {
        settings == StartState.NotSetUp || !allowed -> AtStart.CANCEL
        settings is StartState.Ready -> AtStart.SCHEDULE
        else -> AtStart.LEAVE
    }

    /**
     * The network the round that asks the providers waits for.
     *
     * @param wifiOnly the "Wi-Fi only" switch
     * @return a network without a data limit, such as Wi-Fi, when switched on; else any network
     */
    fun network(wifiOnly: Boolean): NetworkType = if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
}
