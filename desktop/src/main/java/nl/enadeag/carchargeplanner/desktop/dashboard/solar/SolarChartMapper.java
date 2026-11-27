package nl.enadeag.carchargeplanner.desktop.dashboard.solar;

import nl.enadeag.carchargeplanner.desktop.dashboard.solar.SolarChartModel.TickLabel;
import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.desktop.text.TextKey;
import nl.enadeag.carchargeplanner.engine.dashboard.DashboardDay;
import nl.enadeag.carchargeplanner.engine.dashboard.DayChart;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

/**
 * The engine's solar chart of a day in words, with the time axis's marks the desktop shows, in the time zone of the
 * owner's home.
 */
public final class SolarChartMapper {

    /** The clock hours between two marks on the time axis. */
    private static final int TICK_HOURS = 3;

    private final ZoneId zone;
    private final Messages messages;

    /**
     * Makes the mapper.
     *
     * @param zone the time zone of the owner's home, in which the times are told
     * @param messages the texts, numbers and times, in the owner's language
     */
    public SolarChartMapper(ZoneId zone, Messages messages) {
        this.zone = zone;
        this.messages = messages;
    }

    /**
     * A day's chart of expected solar power, the baseload and the charge windows, in words.
     *
     * @param day one of the dashboard's days, with its name and its chart
     * @return the engine's chart with its scale, a readout per step and every third clock hour on the time axis
     */
    public SolarChartModel map(DashboardDay day) {
        var chart = day.chart();
        var readouts = chart.steps().stream()
                .map(step -> messages.text(TextKey.CHART_READOUT, messages.time(step.start(), zone),
                        messages.decimal(step.kw()), messages.decimal(chart.baseloadKw())))
                .toList();
        return new SolarChartModel(chart, messages.text(TextKey.CHART_SCALE, messages.whole(chart.scaleKw())),
                readouts, ticks(chart));
    }

    private static List<TickLabel> ticks(DayChart chart) {
        return chart.ticks().stream()
                .filter(tick -> tick.clockHour() % TICK_HOURS == 0)
                .map(tick -> new TickLabel(tick, String.format(Locale.ROOT, "%02d", tick.clockHour())))
                .toList();
    }
}
