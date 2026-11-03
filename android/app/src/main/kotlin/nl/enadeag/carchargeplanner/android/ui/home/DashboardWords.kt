package nl.enadeag.carchargeplanner.android.ui.home

import android.content.res.Resources
import android.text.format.DateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import nl.enadeag.carchargeplanner.android.R
import nl.enadeag.carchargeplanner.android.form.NumberText
import nl.enadeag.carchargeplanner.android.home.ComingAway
import nl.enadeag.carchargeplanner.android.home.DayKind
import nl.enadeag.carchargeplanner.android.home.DayModel
import nl.enadeag.carchargeplanner.android.home.DayName
import nl.enadeag.carchargeplanner.android.home.Footer
import nl.enadeag.carchargeplanner.android.home.GridAdviceModel
import nl.enadeag.carchargeplanner.engine.plan.Window

/**
 * The dashboard's words in the app's language: days, times in the home's time zone on the phone's 12- or 24-hour
 * clock, and numbers with the language's decimal mark.
 *
 * @param resources the app's texts, in its language
 * @param locale the app's language
 * @param twentyFour whether the phone uses a 24-hour clock
 * @param zone the home's time zone, in which the times are told
 */
internal class DashboardWords(private val resources: Resources, private val locale: Locale, twentyFour: Boolean,
    private val zone: ZoneId) {

    private val clock = clockOf(twentyFour, locale).withZone(zone)
    private val shortDate = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "EEEdMMM"), locale)
    private val numbers = NumberText(locale)

    /**
     * A day's name on its tab.
     *
     * @param day the day
     * @return "Today", or the weekday's short name, such as "Tue"
     */
    fun tab(day: DayModel): String =
        if (day.name == DayName.Today) text(R.string.day_today) else capitalised(weekday(day.date, TextStyle.SHORT))

    /**
     * A day's name as a title, which is also what a screen reader says for its tab.
     *
     * @param day the day
     * @return "Today", "Tomorrow", or the weekday, such as "Sunday"
     */
    fun title(day: DayName): String = when (day) {
        DayName.Today -> text(R.string.day_today)
        DayName.Tomorrow -> text(R.string.day_tomorrow)
        is DayName.Other -> capitalised(weekday(day.date, TextStyle.FULL))
    }

    /**
     * A day's name in the middle of a sentence.
     *
     * @param day the day
     * @return "today", "tomorrow", or the weekday as the language writes it there: "Sunday", "zondag"
     */
    fun inSentence(day: DayName): String = when (day) {
        DayName.Today -> text(R.string.day_today_in_sentence)
        DayName.Tomorrow -> text(R.string.day_tomorrow_in_sentence)
        is DayName.Other -> weekday(day.date, TextStyle.FULL)
    }

    /**
     * A day's name in a list that may reach beyond the coming week.
     *
     * @param day the day
     * @return "Today", "Tomorrow", or the short weekday and date as the phone writes them, such as "Sat, Oct 17"
     */
    fun date(day: DayName): String = when (day) {
        DayName.Today, DayName.Tomorrow -> title(day)
        is DayName.Other -> capitalised(shortDate.format(day.date))
    }

    /**
     * A moment as a clock time in the home's time zone.
     *
     * @param moment the moment
     * @return such as "13:05" or "1:05 PM"
     */
    fun time(moment: Instant): String = clock.format(moment)

    /**
     * A charge window's times.
     *
     * @param window the window
     * @return such as "11:15 – 14:30"
     */
    fun span(window: Window): String = text(R.string.time_span, time(window.start()), time(window.end()))

    /**
     * A charge window's times as a screen reader reads them.
     *
     * @param window the window
     * @return such as "11:15 to 14:30"
     */
    fun spokenSpan(window: Window): String =
        text(R.string.time_span_spoken, time(window.start()), time(window.end()))

    /**
     * A number with one decimal, such as a power or an energy.
     *
     * @param value the number
     * @return such as "2.3" or "2,3"
     */
    fun decimal(value: Double): String = numbers.write(value, 1)

    /**
     * A number rounded to a whole one, such as a percentage.
     *
     * @param value the number
     * @return such as "92"
     */
    fun whole(value: Double): String = numbers.write(value, 0)

    /**
     * What a day holds, under its name.
     *
     * @param day the day
     * @return "Charge window", "Grid charge needed" or "No charge window"
     */
    fun heading(day: DayModel): String = text(when (day.kind) {
        DayKind.SOLAR -> R.string.day_window
        DayKind.GRID -> R.string.day_grid
        DayKind.NONE -> R.string.day_none
    })

    /**
     * A day's note: its later charge windows, why it has none, and the battery level the plan assumed.
     *
     * @param day the day
     * @return the sentences, joined; empty when there are none
     */
    fun note(day: DayModel): String {
        val later = if (day.later.isEmpty()) "" else text(R.string.day_later_windows,
            day.later.joinToString(", ") { text(R.string.day_later_window, span(it), it.amps()) })
        val why = when (day.kind) {
            DayKind.SOLAR -> ""
            DayKind.GRID -> text(R.string.day_grid_why)
            DayKind.NONE -> text(R.string.day_none_why)
        }
        val assumed = day.assumedLevel?.let { text(R.string.day_battery_assumed, whole(it.value())) }.orEmpty()
        return listOf(later, why, assumed).filter { it.isNotEmpty() }.joinToString(" ")
    }

    /**
     * Grid advice's title: how much and when.
     *
     * @param advice the advice
     * @return such as "Charge 8.4 kWh from the grid: Sunday 09:00 – 15:10."
     */
    fun adviceTitle(advice: GridAdviceModel): String {
        val energy = decimal(advice.energyKwh)
        val window = advice.window
        val day = advice.windowDay
        return if (window != null && day != null) {
            text(R.string.advice_title_window, energy, inSentence(day), span(window))
        } else {
            text(R.string.advice_title_no_window, energy, inSentence(advice.lowDay))
        }
    }

    /**
     * Grid advice's detail: why, and what to set.
     *
     * @param advice the advice
     * @return such as "Without it the battery ends Sunday at 17%, below the minimum of 30%. Set 16 A (3.7 kW), …"
     */
    fun adviceDetail(advice: GridAdviceModel): String {
        val why = if (advice.runsEmpty) text(R.string.advice_runs_empty, inSentence(advice.lowDay),
            whole(advice.minimumPct))
            else text(R.string.advice_ends_low, inSentence(advice.lowDay), whole(advice.endLevelPct),
                whole(advice.minimumPct))
        val window = advice.window
        val limit = advice.chargeLimit
        val settings = if (window != null && limit != null) text(R.string.advice_set, window.amps(),
            decimal(window.powerKw()), whole(limit.value()))
            else text(R.string.advice_no_room)
        return "$why $settings"
    }

    /**
     * A one-off away period in a list.
     *
     * @param away the period
     * @return such as "Tomorrow, all day" or "Sat, Oct 17, 10:00 – 16:30"
     */
    fun away(away: ComingAway): String = when (away) {
        is ComingAway.AllDay -> text(R.string.one_off_all_day, date(away.day))
        is ComingAway.PartOfDay -> text(R.string.one_off_part, date(away.day),
            text(R.string.time_span, time(away.start), time(away.end)))
        is ComingAway.Days -> text(R.string.one_off_days, date(away.startDay), time(away.start), date(away.endDay),
            time(away.end))
    }

    /**
     * The footer's line, as the desktop's: the provider's credit, the fallback, updated or stale, busy.
     *
     * @param footer the forecast's source and freshness
     * @return such as "Weather data: Open-Meteo.com (CC BY 4.0) · updated 10:51"
     */
    fun footer(footer: Footer): String {
        val fallback = when (val standIn = footer.fallback) {
            Footer.Fallback.No -> null
            Footer.Fallback.ForUnknown -> text(R.string.footer_fallback_unknown)
            is Footer.Fallback.For -> text(R.string.footer_fallback, standIn.primary)
        }
        val fresh = text(if (footer.stale) R.string.footer_stale else R.string.footer_updated, time(footer.fetchedAt))
        val busy = footer.busyUntil?.let { text(R.string.footer_busy, time(it)) }
        return listOfNotNull(footer.credit, fallback, fresh, busy).joinToString(" · ")
    }

    private fun weekday(date: LocalDate, style: TextStyle): String = date.dayOfWeek.getDisplayName(style, locale)

    private fun capitalised(text: String) = text.replaceFirstChar { it.titlecase(locale) }

    private fun text(id: Int, vararg args: Any): String = resources.getString(id, *args)
}

/**
 * Hours and minutes as the phone's clock shows them.
 *
 * @param twentyFour whether the phone uses a 24-hour clock
 * @param locale the app's language
 * @return such as 13:05 or 1:05 PM
 */
internal fun clockOf(twentyFour: Boolean, locale: Locale): DateTimeFormatter =
    DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, if (twentyFour) "Hm" else "hma"), locale)
