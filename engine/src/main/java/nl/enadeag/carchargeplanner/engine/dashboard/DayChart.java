package nl.enadeag.carchargeplanner.engine.dashboard;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * One day's expected solar power as the dashboard's chart shows it: the solar power per forecast step, the baseload,
 * the charge windows, "now", the whole clock hours and the day's peak. Hours count from the start of the day in
 * real time, so a day with a clock change is 23 or 25 hours long; the ticks carry the clock times.
 *
 * @param fromHour the first hour the chart shows, 0 or more
 * @param toHour the last hour the chart shows, after {@code fromHour}
 * @param scaleKw the power at the top of the chart in kW, finite and more than 0
 * @param steps the forecast steps within the hours shown, in time order; may be empty
 * @param baseloadKw the baseload in kW, finite and 0 or more
 * @param windows the day's charge windows, in time order; may be empty
 * @param now the moment the plan was made; empty on other days than today, and while it is outside the hours shown
 * @param ticks every whole clock hour within the hours shown, in time order
 * @param peak the day's highest solar power; empty when the day has no sun
 */
public record DayChart(
        double fromHour,
        double toHour,
        double scaleKw,
        List<Step> steps,
        double baseloadKw,
        List<WindowBand> windows,
        Optional<Moment> now,
        List<Tick> ticks,
        Optional<Peak> peak) {

    /**
     * Checks the ranges and keeps copies of the lists.
     *
     * @throws IllegalArgumentException when the hours do not run forwards from 0 or later, or the scale or the
     *     baseload is out of range
     */
    public DayChart {
        if (!(fromHour >= 0 && toHour > fromHour)) {
            throw new IllegalArgumentException(
                    "the hours shown must run forwards from 0 or later, not " + fromHour + " to " + toHour);
        }
        if (!(scaleKw > 0) || Double.isInfinite(scaleKw)) {
            throw new IllegalArgumentException("the scale must be finite and more than 0 kW, not " + scaleKw);
        }
        if (!(baseloadKw >= 0) || Double.isInfinite(baseloadKw)) {
            throw new IllegalArgumentException("the baseload must be finite and 0 or more, not " + baseloadKw + " kW");
        }
        steps = List.copyOf(steps);
        windows = List.copyOf(windows);
        ticks = List.copyOf(ticks);
    }

    /**
     * A forecast step.
     *
     * @param startHour the hour the step starts
     * @param endHour the hour the step ends, after {@code startHour}
     * @param kw the expected solar power in kW, 0 or more
     * @param start the moment the step starts
     */
    public record Step(double startHour, double endHour, double kw, Instant start) {

        /**
         * Checks the order and the power.
         *
         * @throws IllegalArgumentException when the step does not run forwards or its power is negative or not a
         *     number
         */
        public Step {
            if (!(endHour > startHour)) {
                throw new IllegalArgumentException("a step must end after it starts, not " + startHour + " to "
                        + endHour);
            }
            if (!(kw >= 0)) {
                throw new IllegalArgumentException("a step's power must not be negative, not " + kw + " kW");
            }
        }
    }

    /**
     * A charge window.
     *
     * @param startHour the hour the window starts
     * @param endHour the hour the window ends, after {@code startHour}
     * @param start the moment the window starts
     * @param end the moment the window ends
     */
    public record WindowBand(double startHour, double endHour, Instant start, Instant end) {

        /**
         * Checks the order.
         *
         * @throws IllegalArgumentException when the window does not run forwards
         */
        public WindowBand {
            if (!(endHour > startHour)) {
                throw new IllegalArgumentException("a window must end after it starts, not " + startHour + " to "
                        + endHour);
            }
        }
    }

    /**
     * A moment on the chart.
     *
     * @param hour the hour from the start of the day
     * @param at the moment itself, for its clock time
     */
    public record Moment(double hour, Instant at) {
    }

    /**
     * A whole clock hour on the time axis.
     *
     * @param hour the hour from the start of the day, in real time
     * @param clockHour the hour on the clock, from 0 to 24, where 24 is the start of the next day
     * @param at the moment, whose clock time is the tick's label
     */
    public record Tick(double hour, int clockHour, Instant at) {
    }

    /**
     * The day's highest solar power.
     *
     * @param kw the power in kW, more than 0
     * @param at the start of the forecast step with that power
     */
    public record Peak(double kw, Instant at) {

        /**
         * Checks the power.
         *
         * @throws IllegalArgumentException when the power is not more than 0
         */
        public Peak {
            if (!(kw > 0)) {
                throw new IllegalArgumentException("a peak's power must be more than 0, not " + kw + " kW");
            }
        }
    }
}
