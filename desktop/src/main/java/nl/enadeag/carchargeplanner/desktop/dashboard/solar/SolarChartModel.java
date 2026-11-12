package nl.enadeag.carchargeplanner.desktop.dashboard.solar;

import nl.enadeag.carchargeplanner.engine.dashboard.DayChart;
import java.util.List;
import java.util.Optional;

/**
 * One day's solar chart as the desktop draws it: the engine's chart with the words the desktop shows and speaks.
 *
 * @param chart the engine's chart of the day, whose hours, power, steps, windows and "now" are drawn
 * @param scaleText the power at the top of the chart in words, such as "4 kW"
 * @param readouts each step of {@code chart} in words, in the same order, such as "13:00: 4.2 kW solar · 0.4 kW house"
 * @param ticks the hours of {@code chart} that the time axis labels, in time order; the desktop labels only some
 * @param spoken the chart in words, for screen readers
 */
public record SolarChartModel(
        DayChart chart,
        String scaleText,
        List<String> readouts,
        List<TickLabel> ticks,
        String spoken) {

    /**
     * Checks that every step has its readout and keeps copies of the lists.
     *
     * @throws IllegalArgumentException when there is not exactly one readout per step of the chart
     */
    public SolarChartModel {
        if (readouts.size() != chart.steps().size()) {
            throw new IllegalArgumentException("each of the " + chart.steps().size()
                    + " steps needs one readout, not " + readouts.size());
        }
        readouts = List.copyOf(readouts);
        ticks = List.copyOf(ticks);
    }

    /**
     * The step to read out at an hour, such as where the pointer is.
     *
     * @param hour the hour from the start of the day; outside the steps, the nearest step is taken
     * @return the step that holds the hour, the nearest one, or empty when there are no steps
     */
    Optional<DayChart.Step> stepAt(double hour) {
        var steps = chart.steps();
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
     * The words of a step.
     *
     * @param step must be one of the chart's steps
     * @return the step's readout
     */
    String readout(DayChart.Step step) {
        return readouts.get(chart.steps().indexOf(step));
    }

    /**
     * The middle of a step, where the curve passes through its power.
     *
     * @param step a forecast step
     * @return the hour halfway between its start and end
     */
    static double midHour(DayChart.Step step) {
        return (step.startHour() + step.endHour()) / 2;
    }

    /**
     * A labelled mark on the time axis.
     *
     * @param tick the engine's whole clock hour the mark is at
     * @param label the clock time, such as "09"
     */
    public record TickLabel(DayChart.Tick tick, String label) {
    }
}
