package nl.enadeag.carchargeplanner.desktop.dashboard.solar;

import nl.enadeag.carchargeplanner.desktop.dashboard.solar.SolarChartModel.TickLabel;
import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.desktop.text.TextKey;
import nl.enadeag.carchargeplanner.engine.dashboard.DashboardDay;
import nl.enadeag.carchargeplanner.engine.dashboard.DayChart;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
     * @return the engine's chart with its scale, a readout per step, every third clock hour on the time axis and the
     *     spoken sentence
     */
    public SolarChartModel map(DashboardDay day) {
        var chart = day.chart();
        var readouts = chart.steps().stream()
                .map(step -> messages.text(TextKey.CHART_READOUT, messages.time(step.start(), zone),
                        messages.decimal(step.kw()), messages.decimal(chart.baseloadKw())))
                .toList();
        return new SolarChartModel(chart, messages.text(TextKey.CHART_SCALE, messages.whole(chart.scaleKw())),
                readouts, ticks(chart), spoken(day));
    }

    private static List<TickLabel> ticks(DayChart chart) {
        return chart.ticks().stream()
                .filter(tick -> tick.clockHour() % TICK_HOURS == 0)
                .map(tick -> new TickLabel(tick, String.format(Locale.ROOT, "%02d", tick.clockHour())))
                .toList();
    }

    private String spoken(DashboardDay day) {
        var chart = day.chart();
        var title = messages.title(day.name());
        if (chart.peak().isEmpty()) {
            return messages.text(TextKey.CHART_SPOKEN_NO_SOLAR, title);
        }
        var peak = chart.peak().orElseThrow();
        var solar = messages.text(TextKey.CHART_SPOKEN, title, messages.decimal(peak.kw()),
                messages.time(peak.at(), zone), messages.decimal(chart.baseloadKw()));
        var windows = chart.windows().isEmpty() ? "" : messages.text(TextKey.CHART_SPOKEN_WINDOWS, chart.windows()
                .stream()
                .map(window -> messages.text(TextKey.CHART_SPOKEN_SPAN, messages.time(window.start(), zone),
                        messages.time(window.end(), zone)))
                .collect(Collectors.joining(", ")));
        var now = chart.now().map(at -> messages.text(TextKey.CHART_SPOKEN_NOW, messages.time(at.at(), zone)))
                .orElse("");
        return sentences(solar, windows, now);
    }

    private static String sentences(String... sentences) {
        return Stream.of(sentences).filter(sentence -> !sentence.isEmpty()).collect(Collectors.joining(" "));
    }
}
