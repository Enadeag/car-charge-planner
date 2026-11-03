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
import nl.enadeag.carchargeplanner.engine.plan.GridAdvice
import nl.enadeag.carchargeplanner.engine.plan.Window

/** Plans made up for the home's tests, in the time zone given; only what the home shows is filled in with care. */
internal object Plans {

    /** The credit Open-Meteo's terms ask for, as the engine gives it. */
    const val CREDIT = "Weather data: Open-Meteo.com (CC BY 4.0)"

    /**
     * A window of 6 A on one phase, all on solar power unless told otherwise.
     *
     * @param zone the time zone of the times
     * @param day the day it is on
     * @param from the hour it starts
     * @param until the hour it ends
     * @param importKwh what it takes from the grid
     */
    fun window(zone: ZoneId, day: LocalDate, from: Int, until: Int, importKwh: Double = 0.0): Window = Window(
        day.atTime(from, 0).atZone(zone).toInstant(), day.atTime(until, 0).atZone(zone).toInstant(), 6, 1.38,
        1.38 * (until - from) - importKwh, importKwh)

    /**
     * Where a forecast came from.
     *
     * @param fetchedAt when it was fetched
     * @param freshness how fresh it is
     * @param role whether the primary or the fallback provider gave it
     * @param primaryName the primary provider's name, when the fallback stood in
     * @param busyUntil the provider's cool-down
     */
    fun source(fetchedAt: Instant, freshness: Freshness = Freshness.FRESH, role: ProviderRole = ProviderRole.PRIMARY,
        primaryName: String = "", busyUntil: Instant? = null) = ForecastSource(ProviderId("open-meteo"),
        "Open-Meteo", CREDIT, fetchedAt, freshness, role, primaryName, Optional.ofNullable(busyUntil))

    /**
     * A plan made at a moment, with one window list per day from that moment's day on; each day starts at 40% and
     * ends its charging 10 points higher per window.
     *
     * @param madeAt when it was made, in the home's time zone
     * @param days each day's windows, today first
     * @param assumed whether the battery level is assumed
     * @param gridAdvice the grid advice, if any
     * @param source where the forecast came from; fetched when the plan was made by default
     */
    fun planned(madeAt: ZonedDateTime, days: List<List<Window>>, assumed: Boolean = true,
        gridAdvice: GridAdvice? = null, source: ForecastSource = source(madeAt.toInstant())): Outlook.Planned {
        val level = Percent(40.0)
        val plans = days.mapIndexed { index, windows ->
            val after = Percent(40.0 + 10.0 * windows.size)
            DayPlan(madeAt.toLocalDate().plusDays(index.toLong()), level, windows, after, after, 0.0, listOf())
        }
        return Outlook.Planned(madeAt.toInstant(), madeAt.zone, plans, level, assumed, Optional.ofNullable(gridAdvice),
            listOf(), source, 0.3, Percent(20.0), Percent(80.0))
    }
}
