package io.github.enadeag.carchargeplanner.desktop;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * One day's expected sun as the solar chart draws it. Times are hours from the start of the day in real time, so a
 * day with a clock change is 23 or 25 hours long; the ticks carry the clock times.
 *
 * @param fromHour the first hour the chart shows, 0 or more
 * @param toHour the last hour the chart shows, after {@code fromHour}
 * @param scaleKw the power at the top of the chart in kW, more than 0
 * @param scaleText the power at the top of the chart in words, such as "4 kW"
 * @param steps the forecast steps within the hours shown, in time order; may be empty
 * @param houseKw the power the house uses on its own in kW, 0 or more
 * @param windows the charge windows of the day, in time order; may be empty
 * @param nowHour the current hour when it lies within the hours shown; empty on other days
 * @param ticks the hours marked on the time axis, in time order
 * @param spoken the chart in words, for screen readers
 */
record SolarCurveModel(
        double fromHour,
        double toHour,
        double scaleKw,
        String scaleText,
        List<Step> steps,
        double houseKw,
        List<Span> windows,
        OptionalDouble nowHour,
        List<Tick> ticks,
        String spoken) {

    /**
     * Checks the ranges and keeps copies of the lists.
     *
     * @throws IllegalArgumentException when the hours are not in order, or the scale or the house's use is out of
     *     range
     */
    SolarCurveModel {
        if (!(fromHour >= 0 && toHour > fromHour)) {
            throw new IllegalArgumentException("the hours shown must run forwards from 0 or later");
        }
        if (!(scaleKw > 0)) {
            throw new IllegalArgumentException("the scale must be more than 0 kW, not " + scaleKw);
        }
        if (!(houseKw >= 0)) {
            throw new IllegalArgumentException("the house's use must not be negative, not " + houseKw);
        }
        steps = List.copyOf(steps);
        windows = List.copyOf(windows);
        ticks = List.copyOf(ticks);
    }

    /**
     * The step to read out at an hour, such as where the pointer is.
     *
     * @param hour the hour from the start of the day; outside the steps, the nearest step is taken
     * @return the step that holds the hour, the nearest one, or empty when there are no steps
     */
    Optional<Step> stepAt(double hour) {
        if (steps.isEmpty()) {
            return Optional.empty();
        }
        for (var step : steps) {
            if (hour < step.endHour()) {
                return Optional.of(step);
            }
        }
        return Optional.of(steps.getLast());
    }

    /**
     * A forecast step.
     *
     * @param startHour the hour the step starts
     * @param endHour the hour the step ends, after {@code startHour}
     * @param kw the expected solar power in kW, 0 or more
     * @param readout the step in words, such as "13:00 – 4.2 kW sun · 0.4 kW house"
     */
    record Step(double startHour, double endHour, double kw, String readout) {

        /**
         * Checks the values.
         *
         * @throws IllegalArgumentException when the step does not run forwards or its power is negative
         */
        Step {
            if (!(endHour > startHour)) {
                throw new IllegalArgumentException("a step must end after it starts");
            }
            if (!(kw >= 0)) {
                throw new IllegalArgumentException("a step's power must not be negative, not " + kw);
            }
        }

        /**
         * The middle of the step, where the curve passes through its power.
         *
         * @return the hour halfway between start and end
         */
        double midHour() {
            return (startHour + endHour) / 2;
        }
    }

    /**
     * A charge window.
     *
     * @param startHour the hour the window starts
     * @param endHour the hour the window ends, after {@code startHour}
     */
    record Span(double startHour, double endHour) {

        /**
         * Checks the order.
         *
         * @throws IllegalArgumentException when the window does not run forwards
         */
        Span {
            if (!(endHour > startHour)) {
                throw new IllegalArgumentException("a window must end after it starts");
            }
        }
    }

    /**
     * A mark on the time axis.
     *
     * @param hour the hour from the start of the day
     * @param label the clock time, such as "09"
     */
    record Tick(double hour, String label) {
    }
}
