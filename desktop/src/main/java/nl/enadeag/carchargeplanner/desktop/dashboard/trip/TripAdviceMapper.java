package nl.enadeag.carchargeplanner.desktop.dashboard.trip;

import nl.enadeag.carchargeplanner.desktop.app.Units;
import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.desktop.text.TextKey;
import nl.enadeag.carchargeplanner.engine.dashboard.DashboardDay;
import nl.enadeag.carchargeplanner.engine.dashboard.DashboardView;
import nl.enadeag.carchargeplanner.engine.dashboard.DayName;
import nl.enadeag.carchargeplanner.engine.dashboard.SolarDay;
import nl.enadeag.carchargeplanner.engine.dashboard.StartLevel;
import nl.enadeag.carchargeplanner.engine.dashboard.TripAdviceView;
import nl.enadeag.carchargeplanner.engine.dashboard.TripGridStart;
import nl.enadeag.carchargeplanner.engine.dashboard.TripPlan;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The engine's advice for each trip in words: the trip, how far to charge, the solar windows and the grid charge it
 * takes, when to start the grid charge and with what charge limit, and the battery level it starts from. The level on
 * solar power alone is left out: the engine gives it at the departure, after the driving in between, so it would read
 * as less than the solar windows bring.
 */
public final class TripAdviceMapper {

    /** Keeps a window's times together on one line when the advice wraps. */
    private static final char NO_BREAK = '\u00A0';

    private final Messages messages;
    private final ZoneId zone;
    private final Units units;

    /**
     * Makes the mapper for one home.
     *
     * @param zone the home's time zone, in which the times are told
     * @param units the units the distances are told in
     * @param messages the texts, in the owner's language
     */
    public TripAdviceMapper(ZoneId zone, Units units, Messages messages) {
        this.messages = messages;
        this.zone = zone;
        this.units = units;
    }

    /**
     * The advice a day's tab shows, in words: with the words of today on today's tab, and the weekdays on another.
     *
     * @param dashboard the engine's dashboard, with the battery level the plan starts from; never null
     * @param day one of the dashboard's days, whose tab shows the advice; never null
     * @return one model per trip whose advice the engine shows with the day, in order of departure; may be empty
     */
    public List<TripAdviceModel> map(DashboardView dashboard, DashboardDay day) {
        var names = day.name() instanceof DayName.Today ? DayNames.RELATIVE : DayNames.WEEKDAYS;
        var level = level(dashboard.startLevel());
        return dashboard.tripAdviceOn(day).stream().map(advice -> map(advice, names, level)).toList();
    }

    private String level(StartLevel level) {
        return messages.text(level.assumed() ? TextKey.TRIP_ADVICE_ASSUMED : TextKey.TRIP_ADVICE_ENTERED,
                messages.whole(level.levelPct().value()));
    }

    private TripAdviceModel map(TripAdviceView advice, DayNames names, String level) {
        var trip = advice.trip();
        var title = messages.text(TextKey.TRIP_ADVICE_TITLE, dayName(advice.departureDay(), names),
                messages.time(trip.departure(), zone), messages.whole(units.distance(trip.distanceKm())),
                units.distanceUnit());
        return new TripAdviceModel(advice.source(), messages.text(TextKey.TRIP_ADVICE_LABEL), title,
                plan(advice, names), level);
    }

    /** How the advice names a day in a sentence. */
    private enum DayNames {
        /** "today", "tonight", "tomorrow", then the weekday: on today's tab, where they mean what they say. */
        RELATIVE,
        /** Always the weekday: on another day's tab, where "tomorrow" would read as the day after that tab. */
        WEEKDAYS
    }

    private String dayName(DayName day, DayNames names) {
        return names == DayNames.RELATIVE ? messages.inSentence(day) : messages.weekday(day.date());
    }

    /** What to charge, when to start the grid charge and what to set for it, and how far the battery falls short. */
    private String plan(TripAdviceView advice, DayNames names) {
        var sentences = new ArrayList<String>();
        switch (advice.plan()) {
            case TripPlan.Enough enough -> sentences.add(messages.text(TextKey.TRIP_ADVICE_ENOUGH,
                    messages.whole(enough.departureLevelPct().value())));
            case TripPlan.Solar solar -> sentences.add(messages.text(TextKey.TRIP_ADVICE_SOLAR,
                    messages.whole(solar.chargeLimitPct().value()), windows(solar.days(), names)));
            case TripPlan.SolarThenGrid solarThenGrid -> {
                var limit = messages.whole(solarThenGrid.chargeLimitPct().value());
                sentences.add(messages.text(TextKey.TRIP_ADVICE_SOLAR_GRID, limit,
                        windows(solarThenGrid.days(), names)));
                grid(solarThenGrid.grid(), limit, names, sentences);
            }
            case TripPlan.Grid grid -> {
                var limit = messages.whole(grid.chargeLimitPct().value());
                sentences.add(messages.text(TextKey.TRIP_ADVICE_GRID, limit));
                grid(grid.grid(), limit, names, sentences);
            }
            case TripPlan.Leaves leaves -> sentences.add(messages.text(TextKey.TRIP_ADVICE_LEAVES,
                    messages.whole(leaves.departureLevelPct().value())));
        }
        advice.shortKm().ifPresent(km -> sentences.add(messages.text(TextKey.TRIP_ADVICE_SHORT,
                messages.whole(Math.ceil(units.distance(km))), units.distanceUnit())));
        return String.join(" ", sentences);
    }

    /** When to start the grid charge, and what to set for it. */
    private void grid(TripGridStart grid, String limit, DayNames names, List<String> sentences) {
        var when = when(grid, names);
        sentences.add(switch (grid.start()) {
            case PLANNED -> messages.text(TextKey.TRIP_ADVICE_START_PLANNED, when);
            case NOW -> messages.text(TextKey.TRIP_ADVICE_START_NOW, when);
            case NOW_OUTSIDE_CHARGING_HOURS -> messages.text(TextKey.TRIP_ADVICE_START_OUTSIDE, when);
        });
        var window = grid.window();
        sentences.add(messages.text(TextKey.ADVICE_SET, window.amps(), messages.decimal(window.powerKw()), limit));
    }

    /** "now" when the start is due; otherwise the day, or tonight on today's tab, with the time. */
    private String when(TripGridStart grid, DayNames names) {
        if (grid.startsNow()) {
            return messages.text(TextKey.TRIP_ADVICE_NOW);
        }
        var day = names == DayNames.RELATIVE && grid.tonight()
                ? messages.text(TextKey.TRIP_ADVICE_TONIGHT)
                : dayName(grid.day(), names);
        return messages.text(TextKey.TRIP_ADVICE_AT, day, messages.time(grid.window().start(), zone));
    }

    /**
     * The windows by their day, each day named once: "today 12:00 – 13:30, 14:00 – 15:00, tomorrow …" or
     * "Friday 12:00 – 13:30, …", each window's times without a break.
     */
    private String windows(List<SolarDay> days, DayNames names) {
        return days.stream()
                .map(day -> dayName(day.day(), names) + " " + day.windows().stream()
                        .map(window -> messages.span(window.start(), window.end(), zone).replace(' ', NO_BREAK))
                        .collect(Collectors.joining(", ")))
                .collect(Collectors.joining(", "));
    }
}
