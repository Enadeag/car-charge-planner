package nl.enadeag.carchargeplanner.android

import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin
import nl.enadeag.carchargeplanner.engine.config.Car
import nl.enadeag.carchargeplanner.engine.config.Charger
import nl.enadeag.carchargeplanner.engine.config.Coordinates
import nl.enadeag.carchargeplanner.engine.config.HomeSystem
import nl.enadeag.carchargeplanner.engine.config.House
import nl.enadeag.carchargeplanner.engine.config.PanelArray
import nl.enadeag.carchargeplanner.engine.config.Percent
import nl.enadeag.carchargeplanner.engine.config.PlanningPreferences
import nl.enadeag.carchargeplanner.engine.forecast.ForecastStep
import nl.enadeag.carchargeplanner.engine.plan.AwayTimes
import nl.enadeag.carchargeplanner.engine.plan.Planner
import nl.enadeag.carchargeplanner.engine.plan.Window

/**
 * The plan the skeleton shows: the engine's [Planner], run on the phone for a made-up home (the engine's `TYPICAL`
 * car, charger, house and panels) and a sunny day made up here, as the app has no settings or forecast provider yet.
 * It shows that the engine plans on Android, not what the owner's home would do.
 */
internal object SunnyDayPlan {

    /** The moment of the day the plan is made, so the whole sunny day is still ahead whenever the app is opened. */
    val PLANNED_AT: LocalTime = LocalTime.of(6, 0)

    /** The battery level the plan starts from. */
    val START_LEVEL = Percent(40.0)

    private const val SUNRISE_HOUR = 6.0
    private const val SUNSET_HOUR = 20.0
    private const val PEAK_KW = 4.5

    /**
     * Today's charge windows.
     *
     * @param today the day to plan, in [zone]
     * @param zone the time zone the day and the windows are in, normally the phone's
     * @return the day's charge windows in time order; empty when the plan has none
     */
    fun windows(today: LocalDate, zone: ZoneId): List<Window> {
        val system = HomeSystem(Coordinates.NOT_CHOSEN, zone, listOf(PanelArray.TYPICAL), House.TYPICAL,
            Car.TYPICAL, Charger.TYPICAL)
        val planner = Planner(system, PlanningPreferences.RECOMMENDED, AwayTimes.NONE)
        val forecast = forecast(today, zone) + forecast(today.plusDays(1), zone)
        val now = today.atTime(PLANNED_AT).atZone(zone).toInstant()
        return planner.plan(forecast, START_LEVEL, now, listOf())
            .firstOrNull { it.date() == today }
            ?.windows()
            .orEmpty()
    }

    /**
     * A cloudless day of hourly steps: no power at night, and a sine from sunrise to sunset that peaks at noon.
     * A day of 23 or 25 hours, at a change of the clocks, simply has fewer or more steps.
     */
    private fun forecast(day: LocalDate, zone: ZoneId): List<ForecastStep> {
        val start = day.atStartOfDay(zone).toInstant()
        val end = day.plusDays(1).atStartOfDay(zone).toInstant()
        return generateSequence(start) { it.plus(Duration.ofHours(1)) }
            .takeWhile { it.isBefore(end) }
            .map { stepStart ->
                val middle = stepStart.plus(Duration.ofMinutes(30)).atZone(zone).toLocalTime()
                val hour = middle.toSecondOfDay() / 3600.0
                val sun = sin(PI * (hour - SUNRISE_HOUR) / (SUNSET_HOUR - SUNRISE_HOUR))
                ForecastStep(stepStart, stepStart.plus(Duration.ofHours(1)), max(0.0, PEAK_KW * sun))
            }
            .toList()
    }
}
