package nl.enadeag.carchargeplanner.android.ui.notify

import android.content.res.Resources
import java.time.ZoneId
import java.util.Locale
import kotlin.math.ceil
import nl.enadeag.carchargeplanner.android.R
import nl.enadeag.carchargeplanner.android.home.DayName
import nl.enadeag.carchargeplanner.android.notify.LockScreen
import nl.enadeag.carchargeplanner.android.notify.NoticePlan
import nl.enadeag.carchargeplanner.android.ui.home.DashboardWords
import nl.enadeag.carchargeplanner.engine.config.Phases
import nl.enadeag.carchargeplanner.engine.notify.Announcement
import nl.enadeag.carchargeplanner.engine.notify.AnnouncementKind
import nl.enadeag.carchargeplanner.engine.notify.BatteryCheck
import nl.enadeag.carchargeplanner.engine.notify.Notice
import nl.enadeag.carchargeplanner.engine.notify.TripChargeStart
import nl.enadeag.carchargeplanner.engine.notify.TripShort
import nl.enadeag.carchargeplanner.engine.plan.TripGridCharge
import nl.enadeag.carchargeplanner.engine.plan.Window
import nl.enadeag.carchargeplanner.engine.trip.Trip

/**
 * A notification's words.
 *
 * @property title its title
 * @property body its text, which may be long
 * @property publicTitle what a locked screen shows instead of both
 */
internal data class NoticeText(val title: String, val body: String, val publicTitle: String)

/**
 * The notifications' words in the app's language, with the desktop's facts: which window, and what to set in the
 * charger's app; what a trip asks of the owner, the trip named by the weekday and time of its departure, as a
 * notification may be read later than it came. Times are in the home's time zone on the phone's 12- or 24-hour clock,
 * a window's times as a screen reader reads them ("11:15 to 14:30"), as the dashboard says them; numbers with the
 * language's decimal mark; a distance in whole km, rounded up.
 *
 * @param resources the app's texts, in its language
 * @param locale the app's language
 * @param twentyFour whether the phone uses a 24-hour clock
 */
internal class NoticeWords(private val resources: Resources, private val locale: Locale,
    private val twentyFour: Boolean) {

    /**
     * The words of a notice.
     *
     * @param notice what the engine tells the owner
     * @param lockScreen what a locked screen shows of it, as its [NoticePlan] says
     * @return its title, its text, and what a locked screen shows: "Charge window soon", or the title
     */
    fun of(notice: Notice, lockScreen: LockScreen): NoticeText {
        val words = DashboardWords(resources, locale, twentyFour, notice.zone())
        val (title, body) = when (notice) {
            is Announcement -> announcement(notice, words)
            is TripChargeStart -> chargeStart(notice, words)
            is BatteryCheck -> text(R.string.notice_level_title) to text(R.string.notice_level_body,
                weekday(notice.trip(), notice.zone(), words), words.time(notice.trip().departure()),
                words.whole(notice.levelPct().value()))
            is TripShort -> text(R.string.notice_short_title) to text(R.string.notice_short_body,
                weekday(notice.trip(), notice.zone(), words), words.time(notice.trip().departure()),
                words.whole(ceil(notice.kmShort())))
        }
        val shown = when (lockScreen) {
            LockScreen.WINDOW_SOON -> text(R.string.notice_window_soon)
            LockScreen.TITLE -> title
        }
        return NoticeText(title, body, shown)
    }

    /**
     * The Problems notification's words.
     *
     * @return "Forecast not updated" and that the app has the details; the title on a locked screen
     */
    fun problems(): NoticeText {
        val title = text(R.string.notice_problems_title)
        return NoticeText(title, text(R.string.notice_problems_body), title)
    }

    /**
     * The battery check's action.
     *
     * @return "Enter battery level"
     */
    fun enterLevel(): String = text(R.string.notice_level_action)

    private fun announcement(announcement: Announcement, words: DashboardWords): Pair<String, String> {
        val window = announcement.window()
        val span = words.spokenSpan(window)
        val setting = setting(window, announcement.phases(), words)
        val energy = words.decimal(window.energyKwh())
        return when (announcement.kind()) {
            AnnouncementKind.SOLAR_WINDOW -> text(R.string.notice_window_title, span) to
                "$setting ${text(R.string.notice_window_body, energy, words.whole(window.solarSharePct().value()))}"
            AnnouncementKind.GRID_CHARGE -> text(R.string.notice_grid_title, span) to
                "$setting ${text(R.string.notice_grid_body, energy, words.whole(announcement.minimumPct().value()),
                    words.whole(announcement.chargeLimitPct().orElseThrow().value()))}"
        }
    }

    private fun chargeStart(start: TripChargeStart, words: DashboardWords): Pair<String, String> {
        val window = start.window()
        val day = weekday(start.trip(), start.zone(), words)
        val departure = words.time(start.trip().departure())
        val limit = words.whole(start.chargeLimitPct().value())
        val setting = setting(window, start.phases(), words)
        return when (start.start()) {
            TripGridCharge.Start.PLANNED -> text(R.string.notice_grid_title, words.spokenSpan(window)) to
                "${text(R.string.notice_trip_start_body, words.time(window.start()), day, departure, limit)} $setting"
            TripGridCharge.Start.NOW -> text(R.string.notice_trip_now_title) to
                "${text(R.string.notice_trip_now_body, day, departure, limit)} $setting"
            TripGridCharge.Start.NOW_OUTSIDE_CHARGING_HOURS -> text(R.string.notice_trip_now_title) to
                "${text(R.string.notice_trip_now_outside_body, day, departure, limit)} $setting"
        }
    }

    /** What to set in the charger's app: the current, per phase on three phases, and the power. */
    private fun setting(window: Window, phases: Phases, words: DashboardWords): String =
        text(if (phases == Phases.THREE) R.string.notice_set_three_phases else R.string.notice_set_one_phase,
            window.amps(), words.decimal(window.powerKw()))

    /** The weekday of a trip's departure in the middle of a sentence, also for today and tomorrow, as the desktop. */
    private fun weekday(trip: Trip, zone: ZoneId, words: DashboardWords): String =
        words.inSentence(DayName.Other(trip.firstDay(zone)))

    private fun text(id: Int, vararg args: Any): String = resources.getString(id, *args)
}
