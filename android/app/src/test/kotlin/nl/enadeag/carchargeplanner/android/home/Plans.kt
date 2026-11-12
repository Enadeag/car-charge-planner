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
import nl.enadeag.carchargeplanner.engine.forecast.ForecastStep
import nl.enadeag.carchargeplanner.engine.forecast.Freshness
import nl.enadeag.carchargeplanner.engine.forecast.ProviderRole
import nl.enadeag.carchargeplanner.engine.plan.DayPlan
import nl.enadeag.carchargeplanner.engine.plan.GridAdvice
import nl.enadeag.carchargeplanner.engine.plan.TripAdvice
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
     * @param keyRefusedBy the providers that refused their API key
     */
    fun source(fetchedAt: Instant, freshness: Freshness = Freshness.FRESH, role: ProviderRole = ProviderRole.PRIMARY,
        primaryName: String = "", busyUntil: Instant? = null, keyRefusedBy: List<ProviderId> = listOf()) =
        ForecastSource(ProviderId("open-meteo"), "Open-Meteo", CREDIT, fetchedAt, freshness, role, primaryName,
            Optional.ofNullable(busyUntil), keyRefusedBy)

    /**
     * Hourly forecast steps of one day, by the clock in a time zone.
     *
     * @param zone the time zone of the hours
     * @param day the day
     * @param kw the power of each hour from 00:00, in kW; the hours after the last have no step
     */
    fun forecast(zone: ZoneId, day: LocalDate, vararg kw: Double): List<ForecastStep> = kw.indices.map {
        ForecastStep(day.atStartOfDay(zone).plusHours(it.toLong()).toInstant(),
            day.atStartOfDay(zone).plusHours(it + 1L).toInstant(), kw[it])
    }

    /**
     * A plan made at a moment, with one window list per day from that moment's day on; each day starts at 40% and
     * ends its charging 10 points higher per window.
     *
     * @param madeAt when it was made, in the home's time zone
     * @param days each day's windows, today first
     * @param assumed whether the battery level is assumed
     * @param gridAdvice the grid advice, if any
     * @param source where the forecast came from; fetched when the plan was made by default
     * @param forecast each day's forecast steps, by its date; none by default
     * @param baseloadKw the baseload in kW
     * @param minimum the minimum charge level in percent
     * @param target the target charge level in percent
     * @param tripAdvice the advice per trip, in order of departure
     * @param endPct each day's level at its end, by its date; the level after its charging by default
     * @param chargeToPct the level each day charges up to, by its date; the target by default, so a day without a
     *     window has too little solar power, and 40 or less makes it need no charging
     */
    fun planned(madeAt: ZonedDateTime, days: List<List<Window>>, assumed: Boolean = true,
        gridAdvice: GridAdvice? = null, source: ForecastSource = source(madeAt.toInstant()),
        forecast: (LocalDate) -> List<ForecastStep> = { listOf() }, baseloadKw: Double = 0.3, minimum: Double = 20.0,
        target: Double = 80.0, tripAdvice: List<TripAdvice> = listOf(),
        endPct: (LocalDate) -> Double? = { null }, chargeToPct: (LocalDate) -> Double = { target }): Outlook.Planned {
        val level = Percent(40.0)
        val plans = days.mapIndexed { index, windows ->
            val after = Percent(40.0 + 10.0 * windows.size)
            val date = madeAt.toLocalDate().plusDays(index.toLong())
            DayPlan(date, level, windows, after, Percent(chargeToPct(date)), endPct(date)?.let { Percent(it) } ?: after,
                0.0, forecast(date))
        }
        return Outlook.Planned(madeAt.toInstant(), madeAt.zone, plans, level, assumed, Optional.ofNullable(gridAdvice),
            tripAdvice, source, baseloadKw, Percent(minimum), Percent(target))
    }
}
