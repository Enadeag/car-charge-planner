package nl.enadeag.carchargeplanner.android.home

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Optional
import nl.enadeag.carchargeplanner.engine.Outlook
import nl.enadeag.carchargeplanner.engine.config.Percent
import nl.enadeag.carchargeplanner.engine.config.ProviderId
import nl.enadeag.carchargeplanner.engine.forecast.ForecastSource
import nl.enadeag.carchargeplanner.engine.forecast.Freshness
import nl.enadeag.carchargeplanner.engine.forecast.ProviderRole
import nl.enadeag.carchargeplanner.engine.plan.DayPlan
import nl.enadeag.carchargeplanner.engine.plan.Window

/** Plans made up for the home's tests, in the time zone given; only what the home shows is filled in with care. */
internal object Plans {

    /**
     * A window of 6 A on one phase.
     *
     * @param zone the time zone of the times
     * @param day the day it is on
     * @param from the hour it starts
     * @param until the hour it ends
     */
    fun window(zone: ZoneId, day: LocalDate, from: Int, until: Int): Window = Window(
        day.atTime(from, 0).atZone(zone).toInstant(), day.atTime(until, 0).atZone(zone).toInstant(), 6, 1.38,
        1.38 * (until - from), 0.0)

    /**
     * A plan made at a moment, with one window list per day from that moment's day on.
     *
     * @param madeAt when it was made, in the home's time zone
     * @param days each day's windows, today first
     * @param assumed whether the battery level is assumed
     */
    fun planned(madeAt: ZonedDateTime, days: List<List<Window>>, assumed: Boolean = true): Outlook.Planned {
        val level = Percent(40.0)
        val plans = days.mapIndexed { index, windows ->
            DayPlan(madeAt.toLocalDate().plusDays(index.toLong()), level, windows, level, level, 0.0, listOf())
        }
        val source = ForecastSource(ProviderId("open-meteo"), "Open-Meteo", "Weather data: Open-Meteo.com",
            madeAt.toInstant(), Freshness.FRESH, ProviderRole.PRIMARY, "", Optional.empty<Instant>())
        return Outlook.Planned(madeAt.toInstant(), madeAt.zone, plans, level, assumed, Optional.empty(), listOf(),
            source, 0.3, Percent(20.0), Percent(80.0))
    }
}
