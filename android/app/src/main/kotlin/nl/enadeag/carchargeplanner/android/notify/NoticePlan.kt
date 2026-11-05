package nl.enadeag.carchargeplanner.android.notify

import java.time.Duration
import java.time.Instant
import nl.enadeag.carchargeplanner.engine.notify.Announcement
import nl.enadeag.carchargeplanner.engine.notify.AnnouncementKind
import nl.enadeag.carchargeplanner.engine.notify.BatteryCheck
import nl.enadeag.carchargeplanner.engine.notify.Notice
import nl.enadeag.carchargeplanner.engine.notify.TripChargeStart
import nl.enadeag.carchargeplanner.engine.notify.TripShort

/**
 * A notification channel of the app. Its id never changes once released, as Android keeps the owner's choices for a
 * channel under its id.
 *
 * @property id the channel's id with Android
 * @property quiet true for a channel of low importance, which makes no sound and does not pop up
 */
enum class NoticeChannel(val id: String, val quiet: Boolean) {

    /** A charge window that starts soon: a solar window, a grid charge, or a trip's grid charge. */
    CHARGE_WINDOWS("charge_windows", false),

    /** What a trip asks of the owner: a battery check, or a charging stop. */
    BATTERY_TRIP_CHECKS("battery_trip_checks", false),

    /** The forecast cannot be updated, and only the owner can fix it. */
    PROBLEMS("problems", true),
}

/** What a notification shows on a locked screen, where its own texts stay hidden (`VISIBILITY_PRIVATE`). */
enum class LockScreen {

    /** "Charge window soon": the notification says no times or settings there. */
    WINDOW_SOON,

    /** The notification's own title, which holds nothing private. */
    TITLE,
}

/** What a tap on a notification opens. */
enum class Tap {

    /** The app, as it is; the dashboard when it was closed. */
    DASHBOARD,

    /** The entry of the battery level on the dashboard. */
    BATTERY_ENTRY,
}

/**
 * How one notice of the engine becomes one notification, apart from its words: under which tag it is posted, so the
 * same notice is never shown twice and one for the same trip replaces the one before, in which channel, until when it
 * stays, what the lock screen shows and what a tap opens.
 *
 * @property tag the notification's tag with Android: the notice's kind and the epoch second of the window's start or
 *     the trip's departure, such as "solar_window:1780300800"
 * @property channel the channel it is posted in
 * @property timeoutAt when Android takes it away: the window's end, or the trip's departure for what a trip asks
 * @property lockScreen what a locked screen shows of it
 * @property tap what a tap, and its action when it has one, opens
 * @property alertOnce true when posting it again under its tag stays silent; false when it alerts again, as a battery
 *     check asked again for the same trip does
 */
data class NoticePlan(val tag: String, val channel: NoticeChannel, val timeoutAt: Instant, val lockScreen: LockScreen,
    val tap: Tap, val alertOnce: Boolean) {

    /**
     * How long the notification is to stay from a moment, such as when it is posted.
     *
     * @param now the moment it is posted
     * @return the time until [timeoutAt]; null when that has come, as the notice is then over and not posted
     */
    fun stayFrom(now: Instant): Duration? = Duration.between(now, timeoutAt).takeIf { !it.isNegative && !it.isZero }

    companion object {

        /** The fixed tag of the Problems notification, of which there is at most one. */
        const val PROBLEMS_TAG = "problems"

        /**
         * The plan of a notice.
         *
         * @param notice what the engine tells the owner
         * @return how it is posted: a window by its start, in the charge windows' channel until its end; a trip's grid
         *     charge by the trip's departure in the same channel until the window's end; a battery check and a short
         *     trip by the departure, in the checks' channel until the departure, a battery check opening the battery
         *     entry and alerting again when it is asked again
         */
        fun of(notice: Notice): NoticePlan = when (notice) {
            is Announcement -> NoticePlan(tag(when (notice.kind()) {
                AnnouncementKind.SOLAR_WINDOW -> "solar_window"
                AnnouncementKind.GRID_CHARGE -> "grid_charge"
            }, notice.window().start()), NoticeChannel.CHARGE_WINDOWS, notice.window().end(), LockScreen.WINDOW_SOON,
                Tap.DASHBOARD, true)
            is TripChargeStart -> NoticePlan(tag("trip_charge_start", notice.trip().departure()),
                NoticeChannel.CHARGE_WINDOWS, notice.window().end(), LockScreen.WINDOW_SOON, Tap.DASHBOARD, true)
            is BatteryCheck -> NoticePlan(tag("battery_check", notice.trip().departure()),
                NoticeChannel.BATTERY_TRIP_CHECKS, notice.trip().departure(), LockScreen.TITLE, Tap.BATTERY_ENTRY,
                false)
            is TripShort -> NoticePlan(tag("trip_short", notice.trip().departure()), NoticeChannel.BATTERY_TRIP_CHECKS,
                notice.trip().departure(), LockScreen.TITLE, Tap.DASHBOARD, true)
        }

        /**
         * A notice's kind as a log names it, without anything of its contents.
         *
         * @param notice the notice
         * @return such as "solar_window" or "battery_check"
         */
        fun kindOf(notice: Notice): String = of(notice).tag.substringBefore(':')

        private fun tag(kind: String, moment: Instant) = "$kind:${moment.epochSecond}"
    }
}
