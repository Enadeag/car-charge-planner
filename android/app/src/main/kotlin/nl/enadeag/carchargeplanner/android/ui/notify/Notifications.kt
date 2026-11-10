package nl.enadeag.carchargeplanner.android.ui.notify

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.time.Clock
import nl.enadeag.carchargeplanner.android.MainActivity
import nl.enadeag.carchargeplanner.android.R
import nl.enadeag.carchargeplanner.android.notify.NoticeChannel
import nl.enadeag.carchargeplanner.android.notify.NoticePlan
import nl.enadeag.carchargeplanner.android.notify.ProblemNotice
import nl.enadeag.carchargeplanner.android.notify.Tap
import nl.enadeag.carchargeplanner.engine.notify.Notice
import nl.enadeag.carchargeplanner.engine.notify.Notifier
import org.slf4j.LoggerFactory

/**
 * Posts what the engine tells the owner as Android notifications, and the Problems notification: one per notice,
 * under the tag of its [NoticePlan], in its channel, until its timeout, private on a locked screen with a short public
 * title, taken away by a tap, which opens the app straight through an immutable intent to its activity. The words are
 * made when it is posted, in the app's language and on the phone's clock as they are then. Nothing is posted while
 * notifications are not allowed; a notification that cannot be posted is logged with its kind only, never thrown, as
 * the engine counts a notice as told either way.
 *
 * @param context the application's context, which outlives every screen
 */
internal class Notifications(private val context: Context, private val clock: Clock) :
    Notifier, ProblemNotice {

    private companion object {
        val LOG = LoggerFactory.getLogger(Notifications::class.java)
    }

    private val manager = NotificationManagerCompat.from(context)

    override fun announce(notice: Notice) {
        val plan = NoticePlan.of(notice)
        post(plan.kind) {
            val stay = plan.stayFrom(clock.instant()) ?: return@post null
            val words = words()
            val builder = builder(plan.channel, words.of(notice, plan.lockScreen), plan.tap, plan.tag)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setTimeoutAfter(stay.toMillis())
                .setOnlyAlertOnce(plan.alertOnce)
            if (plan.tap == Tap.BATTERY_ENTRY) {
                builder.addAction(0, words.enterLevel(), open(Tap.BATTERY_ENTRY, plan.tag))
            }
            plan.tag to builder.build()
        }
    }

    override fun show() {
        post(NoticePlan.PROBLEMS_TAG) {
            val text = words().problems()
            NoticePlan.PROBLEMS_TAG to builder(NoticeChannel.PROBLEMS, text, Tap.DASHBOARD, NoticePlan.PROBLEMS_TAG)
                .setCategory(NotificationCompat.CATEGORY_ERROR)
                .setOnlyAlertOnce(true)
                .build()
        }
    }

    override fun remove() {
        try {
            manager.cancel(NoticePlan.PROBLEMS_TAG, 0)
        } catch (failure: RuntimeException) {
            LOG.warn("The Problems notification could not be taken away", failure)
        }
    }

    /**
     * Posts what [make] makes, under its tag; nothing when it makes nothing or notifications are not allowed.
     *
     * @param kind what the log names it by, never its words
     * @param make the tag and the notification; null when there is nothing to post
     */
    private fun post(kind: String, make: () -> Pair<String, Notification>?) {
        try {
            if (!allowed(context)) {
                return
            }
            val (tag, notification) = make() ?: return
            manager.notify(tag, 0, notification)
        } catch (failure: SecurityException) {
            // the owner may take the permission away between the check and the post; lint asks for this catch
            LOG.warn("A notification of kind {} could not be posted", kind, failure)
        } catch (failure: RuntimeException) {
            LOG.warn("A notification of kind {} could not be posted", kind, failure)
        }
    }

    private fun words(): NoticeWords = NoticeWords(context.resources, context.resources.configuration.locales[0],
        DateFormat.is24HourFormat(context))

    private fun builder(channel: NoticeChannel, text: NoticeText, tap: Tap, tag: String): NotificationCompat.Builder {
        val shown = start(channel)
            .setContentTitle(text.publicTitle)
            .build()
        return start(channel)
            .setContentTitle(text.title)
            .setContentText(text.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text.body))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(shown)
            .setAutoCancel(true)
            .setContentIntent(open(tap, tag))
    }

    /** A notification in the channel with the app's small icon and accent, as both versions of one start. */
    private fun start(channel: NoticeChannel): NotificationCompat.Builder =
        NotificationCompat.Builder(context, channel.id)
            .setSmallIcon(R.drawable.ic_stat_charge)
            .setColor(ContextCompat.getColor(context, R.color.ic_launcher_background))

    /**
     * The app's activity, straight, through its alias that only the app itself can start, without anything another app
     * could fill in. The notification's tag goes along as the intent's data, so the activity takes the notification
     * away also when its action is tapped, which Android's auto-cancel does not do, and two notifications never share
     * one intent.
     */
    private fun open(tap: Tap, tag: String): PendingIntent {
        val intent = Intent().setComponent(MainActivity.noticeTap(context)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .setData(MainActivity.noticeUri(tag))
        if (tap == Tap.BATTERY_ENTRY) {
            intent.action = MainActivity.OPEN_BATTERY_ENTRY
        }
        return PendingIntent.getActivity(context, tap.ordinal, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
}

/**
 * Makes the app's notification channels, or brings their names and descriptions up to the app's language, which
 * Android shows in its settings; the owner's choices for a channel stay. Called at every start, so the Settings row
 * finds them before the first notification.
 */
internal fun createNoticeChannels(context: Context) {
    val channels = NoticeChannel.entries.map { channel ->
        val (name, description) = when (channel) {
            NoticeChannel.CHARGE_WINDOWS ->
                R.string.channel_charge_windows to R.string.channel_charge_windows_description
            NoticeChannel.BATTERY_TRIP_CHECKS ->
                R.string.channel_battery_trip_checks to R.string.channel_battery_trip_checks_description
            NoticeChannel.PROBLEMS -> R.string.channel_problems to R.string.channel_problems_description
        }
        val importance = if (channel.quiet) NotificationManagerCompat.IMPORTANCE_LOW
            else NotificationManagerCompat.IMPORTANCE_DEFAULT
        NotificationChannelCompat.Builder(channel.id, importance)
            .setName(context.getString(name))
            .setDescription(context.getString(description))
            .apply { if (channel.quiet) setSound(null, null) }
            .build()
    }
    NotificationManagerCompat.from(context).createNotificationChannelsCompat(channels)
}

/**
 * Whether the app may post notifications now: Android 13's permission given, and notifications not blocked for the
 * app. Before Android 13 there is no such permission, and only the block counts.
 *
 * @param context any of the app's contexts
 * @param sdk the phone's Android API level; another only in a test
 * @return true when a notification would show
 */
// the permission is asked for only from Android 13, which the check on sdk sees to
@SuppressLint("InlinedApi")
internal fun allowed(context: Context, sdk: Int = Build.VERSION.SDK_INT): Boolean {
    val permitted = sdk < Build.VERSION_CODES.TIRAMISU || ContextCompat.checkSelfPermission(context,
        Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    return permitted && NotificationManagerCompat.from(context).areNotificationsEnabled()
}

/**
 * Opens Android's settings of the app's notifications, where the owner allows them and chooses per channel; the app's
 * own page of Android's settings where a phone has none.
 *
 * @param context a context that can start an activity, such as the screen's
 */
internal fun openNotificationSettings(context: Context) {
    val notifications = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    try {
        context.startActivity(notifications)
    } catch (missing: ActivityNotFoundException) {
        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)))
    }
}
