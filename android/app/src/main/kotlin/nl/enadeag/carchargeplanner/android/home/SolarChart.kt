package nl.enadeag.carchargeplanner.android.home

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import nl.enadeag.carchargeplanner.engine.Outlook
import nl.enadeag.carchargeplanner.engine.forecast.ForecastStep
import nl.enadeag.carchargeplanner.engine.plan.DayPlan

/**
 * One day's expected solar power as the dashboard's chart draws it, as the desktop's: the solar power per forecast
 * step, the house's use, the charge windows, "now" and the hours on the time axis. Hours count from the start of the
 * day in real time, so a day with a clock change is 23 or 25 hours long; the ticks carry the clock times.
 *
 * @property fromHour the first hour the chart shows, 0 or more
 * @property toHour the last hour the chart shows, after [fromHour]
 * @property scaleKw the power at the top of the chart in kW, more than 0; the same for every day of a plan
 * @property steps the forecast steps within the hours shown, in time order; may be empty
 * @property houseKw the power the house uses on its own in kW, 0 or more
 * @property windows the day's charge windows, in time order; may be empty
 * @property now the moment the plan was made, marked as now; only on today, and only within the hours shown
 * @property ticks every whole clock hour within the hours shown, in time order; the screen picks which to mark
 * @property peak the day's highest solar power; null when the day has no sun
 * @throws IllegalArgumentException when the hours do not run forwards from 0 or later, or the scale or the house's
 *     use is out of range
 */
data class SolarChart(
    val fromHour: Double,
    val toHour: Double,
    val scaleKw: Double,
    val steps: List<Step>,
    val houseKw: Double,
    val windows: List<Span>,
    val now: Moment?,
    val ticks: List<Tick>,
    val peak: Peak?,
) {

    init {
        if (!(fromHour >= 0 && toHour > fromHour)) {
            throw IllegalArgumentException(
                "the hours shown must run forwards from 0 or later, not $fromHour to $toHour")
        }
        if (!(scaleKw > 0) || scaleKw.isInfinite()) {
            throw IllegalArgumentException("the scale must be finite and more than 0 kW, not $scaleKw")
        }
        if (!(houseKw >= 0) || houseKw.isInfinite()) {
            throw IllegalArgumentException("the house's use must be finite and 0 or more, not $houseKw kW")
        }
    }

    /**
     * The ticks of every so many clock hours, such as 00, 03, 06.
     *
     * @param hours the clock hours between two ticks, more than 0
     * @return the ticks whose clock hour is a multiple of [hours], in time order
     */
    fun ticksEvery(hours: Int): List<Tick> = ticks.filter { it.clockHour % hours == 0 }

    /**
     * A forecast step.
     *
     * @property startHour the hour the step starts
     * @property endHour the hour the step ends, after [startHour]
     * @property kw the expected solar power in kW, 0 or more
     * @throws IllegalArgumentException when the step does not run forwards or its power is negative
     */
    data class Step(val startHour: Double, val endHour: Double, val kw: Double) {

        init {
            if (!(endHour > startHour)) {
                throw IllegalArgumentException("a step must end after it starts, not $startHour to $endHour")
            }
            if (!(kw >= 0)) {
                throw IllegalArgumentException("a step's power must not be negative, not $kw kW")
            }
        }

        /** The middle of the step, where the curve passes through its power. */
        val midHour: Double get() = (startHour + endHour) / 2
    }

    /**
     * A charge window, in hours from the start of the day.
     *
     * @property startHour the hour it starts
     * @property endHour the hour it ends, after [startHour]
     * @throws IllegalArgumentException when it does not run forwards
     */
    data class Span(val startHour: Double, val endHour: Double) {

        init {
            if (!(endHour > startHour)) {
                throw IllegalArgumentException("a window must end after it starts, not $startHour to $endHour")
            }
        }
    }

    /**
     * A moment on the chart.
     *
     * @property hour the hour from the start of the day
     * @property at the moment itself, for its clock time
     */
    data class Moment(val hour: Double, val at: Instant)

    /**
     * A whole clock hour on the time axis.
     *
     * @property hour the hour from the start of the day, in real time
     * @property clockHour the hour on the clock, from 0 to 24, where 24 is the start of the next day
     * @property at the moment, whose clock time is the tick's label
     */
    data class Tick(val hour: Double, val clockHour: Int, val at: Instant)

    /**
     * The day's highest solar power.
     *
     * @property kw the power in kW
     * @property at the start of the forecast step with that power
     */
    data class Peak(val kw: Double, val at: Instant)
}

/**
 * The days of one plan as the solar chart draws them, in the home's time zone, as the desktop's `SolarChartMapper`
 * does. All days share one power scale, so they can be compared.
 *
 * @param planned the plan whose time zone, moment of making, house use and days the charts show
 */
class SolarChartMapper(private val planned: Outlook.Planned) {

    private companion object {
        /** Less power than this counts as no sun: a forecast's dawn and dusk. */
        const val SUN_KW = 0.01

        /** The chart shows this many hours before the first sun and after the last. */
        const val MARGIN_HOURS = 1

        /** The hours a day without sun shows. */
        const val DARK_FROM = 6.0
        const val DARK_TO = 20.0
    }

    private val zone: ZoneId = planned.zone()
    private val today: LocalDate = planned.madeAt().atZone(zone).toLocalDate()
    private val scaleKw: Double = max(1.0, ceil(max(
        planned.days().flatMap { it.forecast() }.maxOfOrNull(ForecastStep::powerKw) ?: 0.0, planned.baseloadKw())))

    /**
     * A day's chart.
     *
     * @param day one of the plan's days
     * @return the chart, showing the hours with sun and an hour on either side; 06:00 to 20:00 for a day without sun
     */
    fun map(day: DayPlan): SolarChart {
        val start = day.date().atStartOfDay(zone)
        val (from, to) = shownHours(day, start)
        val steps = day.forecast()
            .filter { hoursFrom(start, it.end()) > from && hoursFrom(start, it.start()) < to }
            .map { SolarChart.Step(hoursFrom(start, it.start()), hoursFrom(start, it.end()), it.powerKw()) }
        val windows = day.windows().map { SolarChart.Span(hoursFrom(start, it.start()), hoursFrom(start, it.end())) }
        val madeAt = hoursFrom(start, planned.madeAt())
        val now = if (day.date() == today && madeAt in from..to) SolarChart.Moment(madeAt, planned.madeAt()) else null
        val peak = day.forecast().maxByOrNull(ForecastStep::powerKw)?.takeIf { it.powerKw() > SUN_KW }
            ?.let { SolarChart.Peak(it.powerKw(), it.start()) }
        return SolarChart(from, to, scaleKw, steps, planned.baseloadKw(), windows, now, ticks(start, from, to), peak)
    }

    /**
     * From the hour before the first sun to the hour after the last, within the day, or 06:00 to 20:00 for a day
     * without sun.
     */
    private fun shownHours(day: DayPlan, start: ZonedDateTime): Pair<Double, Double> {
        val sunny = day.forecast().filter { it.powerKw() > SUN_KW }
        if (sunny.isEmpty()) {
            return DARK_FROM to DARK_TO
        }
        val dayHours = hoursFrom(start, start.toLocalDate().plusDays(1).atStartOfDay(zone).toInstant())
        return max(0.0, floor(hoursFrom(start, sunny.first().start())) - MARGIN_HOURS) to
            min(dayHours, ceil(hoursFrom(start, sunny.last().end())) + MARGIN_HOURS)
    }

    /** Every whole clock hour that lies within the hours shown; one the clocks skip on the short day is left out. */
    private fun ticks(start: ZonedDateTime, from: Double, to: Double): List<SolarChart.Tick> =
        (0..24).mapNotNull { clockHour ->
            val at = if (clockHour == 24) start.toLocalDate().plusDays(1).atStartOfDay(zone)
                else start.toLocalDate().atTime(clockHour, 0).atZone(zone)
            val hours = hoursFrom(start, at.toInstant())
            val exists = clockHour == 24 || at.hour == clockHour
            if (exists && hours in from..to) SolarChart.Tick(hours, clockHour, at.toInstant()) else null
        }

    private fun hoursFrom(start: ZonedDateTime, moment: Instant): Double =
        Duration.between(start.toInstant(), moment).toNanos() / 3_600e9
}
