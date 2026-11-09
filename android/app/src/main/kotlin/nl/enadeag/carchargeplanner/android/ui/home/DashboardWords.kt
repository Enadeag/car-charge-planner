package nl.enadeag.carchargeplanner.android.ui.home

import android.content.res.Resources
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import nl.enadeag.carchargeplanner.android.R
import nl.enadeag.carchargeplanner.android.form.NumberText
import nl.enadeag.carchargeplanner.android.home.ComingAway
import nl.enadeag.carchargeplanner.android.home.Footer
import nl.enadeag.carchargeplanner.android.home.ListedTrip
import nl.enadeag.carchargeplanner.android.home.SourceFigure
import nl.enadeag.carchargeplanner.android.home.TripAdviceModel
import nl.enadeag.carchargeplanner.engine.config.Percent
import nl.enadeag.carchargeplanner.engine.dashboard.BatteryLevels
import nl.enadeag.carchargeplanner.engine.dashboard.ChargeLevels
import nl.enadeag.carchargeplanner.engine.dashboard.DashboardDay
import nl.enadeag.carchargeplanner.engine.dashboard.DayChart
import nl.enadeag.carchargeplanner.engine.dashboard.DayKind
import nl.enadeag.carchargeplanner.engine.dashboard.DayName
import nl.enadeag.carchargeplanner.engine.dashboard.GridAdviceView
import nl.enadeag.carchargeplanner.engine.dashboard.NoWindowReason
import nl.enadeag.carchargeplanner.engine.plan.TripGridCharge
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
    private val hour = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale,
        if (twentyFour) "H" else "h"), locale).withZone(zone)
    private val shortDate = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "EEEdMMM"), locale)
    private val longDate = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM"), locale)
    private val numbers = NumberText(locale)

    /**
     * A day's name on its tab.
     *
     * @param day the day
     * @return "Today", or the weekday's short name, such as "Tue"
     */
    fun tab(day: DashboardDay): String = if (day.name() is DayName.Today) text(R.string.day_today)
        else capitalised(weekday(day.name().date(), TextStyle.SHORT))

    /**
     * A day's name as a title, which is also what a screen reader says for its tab.
     *
     * @param day the day
     * @return "Today", "Tomorrow", or the weekday, such as "Sunday"
     */
    fun title(day: DayName): String = when (day) {
        is DayName.Today -> text(R.string.day_today)
        is DayName.Tomorrow -> text(R.string.day_tomorrow)
        is DayName.Other -> capitalised(weekday(day.date(), TextStyle.FULL))
    }

    /**
     * A day's name in the middle of a sentence.
     *
     * @param day the day
     * @return "today", "tomorrow", or the weekday as the language writes it there: "Sunday", "zondag"
     */
    fun inSentence(day: DayName): String = when (day) {
        is DayName.Today -> text(R.string.day_today_in_sentence)
        is DayName.Tomorrow -> text(R.string.day_tomorrow_in_sentence)
        is DayName.Other -> weekday(day.date(), TextStyle.FULL)
    }

    /**
     * A day's name in a list that may reach beyond the coming week.
     *
     * @param day the day
     * @return "Today", "Tomorrow", or the short weekday and date as the phone writes them, such as "Sat, Oct 17"
     */
    fun date(day: DayName): String = when (day) {
        is DayName.Today, is DayName.Tomorrow -> title(day)
        is DayName.Other -> capitalised(shortDate.format(day.date()))
    }

    /**
     * A day by its weekday and date, in the middle of a sentence, as the one-off away entry's title names it.
     *
     * @param date the day
     * @return such as "Thursday, November 5" or "donderdag 5 november", as the phone writes them
     */
    fun day(date: LocalDate): String = longDate.format(date)

    /**
     * A time of day as the phone's clock shows it.
     *
     * @param time the time
     * @return such as "09:00" or "9:00 AM"
     */
    fun time(time: LocalTime): String = clock.format(time)

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
     * A figure by source, as its chip shows it.
     *
     * @param figure the figure
     * @return such as "92% solar", "Battery 50 → 74%", on the day a trip leaves "Battery 50 → 74%, 70% after the
     *     trip", or "0.4 kWh from the grid"
     */
    fun figure(figure: SourceFigure): String = when (figure) {
        is SourceFigure.SolarShare -> text(R.string.day_solar_share, whole(figure.share.value()))
        is SourceFigure.Battery -> battery(figure.levels, R.string.day_battery, R.string.day_battery_trip,
            R.string.day_battery_charge_trip)
        is SourceFigure.FromGrid -> text(R.string.day_from_grid, decimal(figure.kwh))
    }

    /**
     * A figure by source as a screen reader reads it, when its chip reads badly aloud.
     *
     * @param figure the figure
     * @return such as "Battery from 50% to 74%", or "Battery from 50% to 74%, 70% after the trip"; null when the
     *     chip's text reads well
     */
    fun spokenFigure(figure: SourceFigure): String? = when (figure) {
        is SourceFigure.Battery -> battery(figure.levels, R.string.day_battery_spoken,
            R.string.day_battery_trip_spoken, R.string.day_battery_charge_trip_spoken)
        is SourceFigure.SolarShare, is SourceFigure.FromGrid -> null
    }

    /**
     * The battery levels in one of three wordings: the start and after charging; on the day a trip leaves, the start
     * and the end of the day after the trip; or, with a charge before the trip, all three.
     */
    private fun battery(levels: BatteryLevels, plain: Int, trip: Int, chargeAndTrip: Int): String {
        val start = whole(levels.startPct().value())
        val after = whole(levels.afterPct().value())
        val end = levels.afterTripPct().orElse(null)?.let { whole(it.value()) } ?: return text(plain, start, after)
        return if (levels.chargesBeforeTrip()) text(chargeAndTrip, start, after, end) else text(trip, start, end)
    }

    /**
     * What a day holds, under its name.
     *
     * @param day the day
     * @return "Charge window", "Grid charge needed" or "No charge window"
     */
    fun heading(day: DashboardDay): String = text(when (day.kind()) {
        DayKind.SOLAR -> R.string.day_window
        DayKind.GRID -> R.string.day_grid
        DayKind.NONE -> R.string.day_none
    })

    /**
     * A day's note: its later charge windows, why it has none, and the battery level the plan assumed with how to
     * enter the one the car shows.
     *
     * @param day the day, with why it has no charge window; the day a trip leaves says nothing of that
     * @return the sentences, joined; empty when there are none
     */
    fun note(day: DashboardDay): String {
        val later = if (day.later().isEmpty()) "" else text(R.string.day_later_windows,
            day.later().joinToString(", ") { text(R.string.day_later_window, span(it), it.amps()) })
        val because = when (day.noWindow().orElse(null)) {
            null, NoWindowReason.DEPARTURE -> ""
            NoWindowReason.GRID -> text(R.string.day_grid_why)
            NoWindowReason.AWAY -> text(R.string.day_none_trip)
            NoWindowReason.NOT_NEEDED -> text(R.string.day_none_why)
        }
        val assumed = day.assumedLevel().orElse(null)?.let { text(R.string.day_battery_assumed, whole(it.value())) }
            .orEmpty()
        return listOf(later, because, assumed).filter { it.isNotEmpty() }.joinToString(" ")
    }

    /**
     * Grid advice's title: how much and when.
     *
     * @param advice the advice
     * @return such as "Charge 8.4 kWh from the grid: Sunday 09:00 – 15:10."
     */
    fun adviceTitle(advice: GridAdviceView): String {
        val energy = decimal(advice.energyKwh())
        val charge = advice.charge().orElse(null)
        return if (charge != null) {
            text(R.string.advice_title_window, energy, inSentence(charge.day()), span(charge.window()))
        } else {
            text(R.string.advice_title_no_window, energy, inSentence(advice.day()))
        }
    }

    /**
     * Grid advice's detail: why, and what to set.
     *
     * @param advice the advice
     * @return such as "Without it the battery ends Sunday at 17%, below the minimum of 30%. Set 16 A (3.7 kW), …"
     */
    fun adviceDetail(advice: GridAdviceView): String {
        val minimum = whole(advice.minimumPct().value())
        val why = if (advice.runsEmpty()) text(R.string.advice_runs_empty, inSentence(advice.day()), minimum)
            else text(R.string.advice_ends_low, inSentence(advice.day()), whole(advice.endLevelPct()), minimum)
        val charge = advice.charge().orElse(null)
        val settings = if (charge != null) text(R.string.advice_set, charge.window().amps(),
            decimal(charge.window().powerKw()), whole(charge.chargeLimitPct().value()))
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
     * A trip in a list, as the desktop's: its times alone when it returns on the day it leaves, otherwise each with
     * its day, and its distance in whole km.
     *
     * @param trip the trip
     * @return such as "Away 07:30 – 18:00 · 300 km there and back" or "Away Tomorrow 07:30 – Wed, Jun 3 18:00 · …"
     */
    fun trip(trip: ListedTrip): String = text(R.string.trip_line, if (trip.sameDay) {
        text(R.string.time_span, time(trip.departure), time(trip.returnTime))
    } else {
        text(R.string.trip_days, date(trip.departureDay), time(trip.departure), date(trip.returnDay),
            time(trip.returnTime))
    }, tripDistance(trip))

    /**
     * A trip as a screen reader reads it.
     *
     * @param trip the trip
     * @return such as "Away from 07:30 to 18:00, 300 km there and back"
     */
    fun spokenTrip(trip: ListedTrip): String = text(R.string.trip_line_spoken, if (trip.sameDay) {
        text(R.string.time_span_spoken, time(trip.departure), time(trip.returnTime))
    } else {
        text(R.string.trip_days_spoken, date(trip.departureDay), time(trip.departure), date(trip.returnDay),
            time(trip.returnTime))
    }, tripDistance(trip))

    private fun tripDistance(trip: ListedTrip) = text(R.string.trip_distance, whole(trip.trip.distanceKm()))

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

    /**
     * A tick's label on the chart's time axis: the hour alone, on the phone's clock.
     *
     * @param tick the tick
     * @return such as "15" or "3 PM"
     */
    fun tick(tick: DayChart.Tick): String = hour.format(tick.at())

    /**
     * The power at the top of the chart.
     *
     * @param kw the power in kW
     * @return such as "4 kW"
     */
    fun scale(kw: Double): String = text(R.string.chart_scale, whole(kw))

    /**
     * A day's chart in words, as the desktop's: its peak and the house's use, its charge windows and, today, now.
     * A screen reader reads it for the chart, and it shows under the chart.
     *
     * @param day the day, with its chart and charge windows
     * @return such as "Today, expected solar power: peak 4.2 kW around 13:00; the house uses about 0.4 kW. Charging:
     *     13:00 to 15:00. Now 09:00."; for a day without sun only that it has none
     */
    fun chart(day: DashboardDay): String {
        val title = title(day.name())
        val peak = day.chart().peak().orElse(null) ?: return text(R.string.chart_spoken_no_solar, title)
        val solar = text(R.string.chart_spoken, title, decimal(peak.kw()), time(peak.at()),
            decimal(day.chart().baseloadKw()))
        val windows = listOfNotNull(day.main().orElse(null)?.window()) + day.later()
        val charging = if (windows.isEmpty()) "" else text(R.string.chart_spoken_windows,
            windows.joinToString(", ") { spokenSpan(it) })
        val now = day.chart().now().orElse(null)?.let { text(R.string.chart_spoken_now, time(it.at())) }.orEmpty()
        return listOf(solar, charging, now).filter { it.isNotEmpty() }.joinToString(" ")
    }

    /**
     * The battery levels beside the bar.
     *
     * @param battery the levels at the start of the day and after its charging
     * @return such as "50 → 74%", or on the day a trip leaves "50 → 74%, 70% after the trip"
     */
    fun batteryLevels(battery: BatteryLevels): String =
        battery(battery, R.string.battery_levels, R.string.battery_levels_trip, R.string.battery_levels_charge_trip)

    /**
     * The minimum's mark on the battery bar.
     *
     * @param levels the charge levels
     * @return such as "min 30%"
     */
    fun minimum(levels: ChargeLevels): String = text(R.string.battery_minimum, whole(levels.minimumPct().value()))

    /**
     * The target's mark on the battery bar.
     *
     * @param levels the charge levels
     * @return such as "target 80%"
     */
    fun target(levels: ChargeLevels): String = text(R.string.battery_target, whole(levels.targetPct().value()))

    /**
     * A day's battery bar in words, as a screen reader reads it.
     *
     * @param day the day, with its levels
     * @param levels the minimum and target charge levels
     * @return such as "Car battery 50% now, 74% after charging; minimum 30%, target 80%." today, followed by that the
     *     level is assumed and how to enter it while it is, and "… at the start of Sunday, …" on another day; on the
     *     day a trip leaves, followed by the level after the trip
     */
    fun battery(day: DashboardDay, levels: ChargeLevels): String {
        val start = whole(day.battery().startPct().value())
        val after = whole(day.battery().afterPct().value())
        val minimum = whole(levels.minimumPct().value())
        val target = whole(levels.targetPct().value())
        val said = if (day.name() !is DayName.Today) {
            text(R.string.battery_spoken_day, start, inSentence(day.name()), after, minimum, target)
        } else {
            text(R.string.battery_spoken_today, start, after, minimum, target)
        }
        val assumed = if (day.assumedLevel().isPresent) text(R.string.battery_assumed) else ""
        val afterTrip = day.battery().afterTripPct().orElse(null)
            ?.let { text(R.string.battery_spoken_after_trip, whole(it.value())) }
            .orEmpty()
        return listOf(said, assumed, afterTrip).filter { it.isNotEmpty() }.joinToString(" ")
    }

    /**
     * A day in the middle of a trip advice's sentence, as the desktop names it: on today's tab "today", "tomorrow" or
     * the weekday, as they mean what they say there; on any other tab always the weekday, where "tomorrow" would read
     * as the day after that tab.
     *
     * @param date the day to name
     * @param today the day the plan was made, in the home's time zone
     * @param onToday true on today's tab
     * @return such as "tomorrow", "Saturday" or "zaterdag"
     */
    fun adviceDay(date: LocalDate, today: LocalDate, onToday: Boolean): String =
        if (onToday) inSentence(DayName.of(date, today)) else weekday(date)

    /**
     * A day's weekday in the middle of a sentence.
     *
     * @param date the day
     * @return such as "Saturday" in English and "zaterdag" in Dutch
     */
    fun weekday(date: LocalDate): String = weekday(date, TextStyle.FULL)

    /**
     * A trip's advice in words, as the desktop's: its title, its plan, the battery level it starts from, and all of
     * it in one sentence for a screen reader.
     *
     * @param advice the advice
     * @param tab the day of the tab it shows on, whose days it names as [adviceDay] does
     * @param today the day the plan was made, in the home's time zone
     * @return the words
     */
    fun tripAdvice(advice: TripAdviceModel, tab: LocalDate, today: LocalDate): TripAdviceText {
        val onToday = tab == today
        val day = { date: LocalDate -> adviceDay(date, today, onToday) }
        val title = text(R.string.trip_advice_title, day(advice.departureDay), time(advice.departure),
            whole(advice.distanceKm))
        val plan = planText(advice, day, onToday, today, ::unbrokenSpan)
        val spokenPlan = planText(advice, day, onToday, today, ::spokenSpan)
        val level = text(if (advice.levelAssumed) R.string.trip_advice_assumed else R.string.trip_advice_entered,
            whole(advice.level.value()))
        val label = text(R.string.trip_advice_label)
        return TripAdviceText(label, title, plan, level,
            text(R.string.trip_advice_spoken, label, title, spokenPlan, level))
    }

    /** What to charge and when, each solar window's times told by [span]: as shown, or as a screen reader says them. */
    private fun planText(advice: TripAdviceModel, day: (LocalDate) -> String, onToday: Boolean, today: LocalDate,
        span: (Window) -> String): String {
        val sentences = when (val plan = advice.plan) {
            is TripAdviceModel.Plan.Enough ->
                listOf(text(R.string.trip_advice_enough, whole(plan.departurePct.value())))
            is TripAdviceModel.Plan.Solar ->
                listOf(text(R.string.trip_advice_solar, whole(plan.limit.value()), windows(plan.windows, day, span)))
            is TripAdviceModel.Plan.SolarThenGrid -> listOf(text(R.string.trip_advice_solar_grid,
                whole(plan.limit.value()), windows(plan.windows, day, span))) +
                grid(plan.grid, plan.limit, day, onToday, today)
            is TripAdviceModel.Plan.Grid -> listOf(text(R.string.trip_advice_grid, whole(plan.limit.value()))) +
                grid(plan.grid, plan.limit, day, onToday, today)
            is TripAdviceModel.Plan.Leaves ->
                listOf(text(R.string.trip_advice_leaves, whole(plan.departurePct.value())))
        }
        val short = advice.shortKm?.let { text(R.string.trip_advice_short, it.toString()) }
        return (sentences + listOfNotNull(short)).joinToString(" ")
    }

    /** When to start the grid charge, and what to set for it. */
    private fun grid(grid: TripAdviceModel.GridStart, limit: Percent, day: (LocalDate) -> String, onToday: Boolean,
        today: LocalDate): List<String> {
        val start = grid.window.start()
        val local = start.atZone(zone)
        val at = when {
            grid.now -> text(R.string.trip_advice_now)
            // "tonight" means what it says only on today's tab
            onToday && local.toLocalDate() == today && !local.toLocalTime().isBefore(EVENING) ->
                text(R.string.trip_advice_at, text(R.string.trip_advice_tonight), time(start))
            else -> text(R.string.trip_advice_at, day(local.toLocalDate()), time(start))
        }
        val starts = text(when (grid.start) {
            TripGridCharge.Start.PLANNED -> R.string.trip_advice_start_planned
            TripGridCharge.Start.NOW -> R.string.trip_advice_start_now
            TripGridCharge.Start.NOW_OUTSIDE_CHARGING_HOURS -> R.string.trip_advice_start_outside
        }, at)
        return listOf(starts, text(R.string.advice_set, grid.window.amps(), decimal(grid.window.powerKw()),
            whole(limit.value())))
    }

    /**
     * The solar windows grouped by their day, each day named once: "today 12:00 – 13:30, 14:00 – 15:00, tomorrow …",
     * each window's times told by [span].
     */
    private fun windows(days: List<TripAdviceModel.DayWindows>, day: (LocalDate) -> String,
        span: (Window) -> String): String =
        days.joinToString(", ") { inDay -> day(inDay.day) + " " + inDay.windows.joinToString(", ") { span(it) } }

    /**
     * A window's span with no break around its dash: the span's own spaces become no-break ones, not those a 12-hour
     * clock's times may hold before their AM or PM.
     */
    private fun unbrokenSpan(window: Window): String =
        resources.getString(R.string.time_span).replace(' ', NO_BREAK).format(locale, time(window.start()),
            time(window.end()))

    private fun weekday(date: LocalDate, style: TextStyle): String = date.dayOfWeek.getDisplayName(style, locale)

    private fun capitalised(text: String) = text.replaceFirstChar { it.titlecase(locale) }

    private fun text(id: Int, vararg args: Any): String = resources.getString(id, *args)

    private companion object {
        /** From this hour a start later today is "tonight". */
        val EVENING: LocalTime = LocalTime.of(18, 0)

        /** Keeps a window's times together on one line. */
        const val NO_BREAK = '\u00A0'
    }
}

/**
 * A trip's advice in words.
 *
 * @property label its heading: "Trip advice"
 * @property title the trip: "Trip tomorrow at 08:15, 150 km there and back."
 * @property plan what to charge and when: "Charge to 85%: on solar power today 12:00 – 15:00, then from the grid. …"
 * @property level the battery level it starts from: "Assuming a battery level of 45%."
 * @property spoken all of it in one sentence, as a screen reader reads it
 */
internal data class TripAdviceText(val label: String, val title: String, val plan: String, val level: String,
    val spoken: String)

/**
 * Hours and minutes as the phone's clock shows them.
 *
 * @param twentyFour whether the phone uses a 24-hour clock
 * @param locale the app's language
 * @return such as 13:05 or 1:05 PM
 */
internal fun clockOf(twentyFour: Boolean, locale: Locale): DateTimeFormatter =
    DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, if (twentyFour) "Hm" else "hma"), locale)

/** The dashboard's words in the app's language and the phone's clock, with the times in this zone. */
@Composable
internal fun dashboardWords(zone: ZoneId): DashboardWords {
    val resources = LocalResources.current
    val locale = LocalConfiguration.current.locales[0]
    val twentyFour = DateFormat.is24HourFormat(LocalContext.current)
    return remember(resources, locale, twentyFour, zone) { DashboardWords(resources, locale, twentyFour, zone) }
}
