package nl.enadeag.carchargeplanner.android.home

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Optional
import kotlin.math.max
import nl.enadeag.carchargeplanner.engine.Outlook
import nl.enadeag.carchargeplanner.engine.config.Percent
import nl.enadeag.carchargeplanner.engine.dashboard.TripAdviceView
import nl.enadeag.carchargeplanner.engine.plan.TripAdvice
import nl.enadeag.carchargeplanner.engine.plan.TripGridCharge
import nl.enadeag.carchargeplanner.engine.plan.TripGridCharge.Start
import nl.enadeag.carchargeplanner.engine.plan.Window
import nl.enadeag.carchargeplanner.engine.trip.Trip

/** The desktop's `TripAdviceMapperTest` advice, as the engine's dashboard gives it, for the tests of its words. */
internal object TripAdvices {

    val BRUSSELS: ZoneId = ZoneId.of("Europe/Brussels")

    /** A Friday. */
    val TODAY: LocalDate = LocalDate.of(2026, 7, 3)
    val TOMORROW: LocalDate = TODAY.plusDays(1)
    val MADE_AT: Instant = at(TODAY, 10, 20)

    /** Tomorrow from 08:15 to 19:00, 150 km. */
    val TRIP = Trip(at(TOMORROW, 8, 15), at(TOMORROW, 19, 0), 150.0, false)
    val SOLAR = Window(at(TODAY, 12, 0), at(TODAY, 15, 0), 8, 1.8, 5.2, 0.1)

    fun at(day: LocalDate, hour: Int, minute: Int): Instant = day.atTime(hour, minute).atZone(BRUSSELS).toInstant()

    /** A grid charge of 16 A on one phase, from a start to an hour later. */
    fun grid(start: Instant, why: Start): TripGridCharge =
        TripGridCharge(Window(start, start.plusSeconds(3600), 16, 3.7, 0.0, 3.7), why)

    fun advice(neededPct: Double, solar: List<Window>, solarPct: Double, grid: TripGridCharge?,
        departurePct: Double, kmShort: Double, trip: Trip = TRIP): TripAdvice =
        TripAdvice(trip, Percent(neededPct), solar, Percent(solarPct), Optional.ofNullable(grid),
            Percent(departurePct), kmShort, Percent(max(0.0, departurePct - 52)))

    /** As the engine's dashboard gives it: made at 10:20 today from a battery level of 45%, with a target of 80%. */
    fun model(advice: TripAdvice, assumed: Boolean = true): TripAdviceView =
        Outlook.Planned(MADE_AT, BRUSSELS, listOf(), Percent(45.0), assumed, Optional.empty(), listOf(advice),
            Plans.source(MADE_AT), 0.3, Percent(30.0), Percent(80.0))
            .dashboard(listOf(), listOf()).tripAdvice().single()
}
