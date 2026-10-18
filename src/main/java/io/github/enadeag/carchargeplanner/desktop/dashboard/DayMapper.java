package io.github.enadeag.carchargeplanner.desktop.dashboard;

import io.github.enadeag.carchargeplanner.desktop.dashboard.Chip.Source;
import io.github.enadeag.carchargeplanner.desktop.text.Messages;
import io.github.enadeag.carchargeplanner.desktop.text.TextKey;
import io.github.enadeag.carchargeplanner.engine.Outlook;
import io.github.enadeag.carchargeplanner.engine.plan.DayPlan;
import io.github.enadeag.carchargeplanner.engine.plan.GridAdvice;
import io.github.enadeag.carchargeplanner.engine.plan.Window;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** The days of a plan as the dashboard's tabs show them, in the time zone of the owner's home. */
final class DayMapper {

    /** Less grid energy than this is shown as none: it is within the forecast's precision. */
    private static final double NO_GRID_KWH = 0.05;

    private final Messages messages;
    private final ZoneId zone;
    private final LocalDate today;
    private final String todaysNote;
    /** The day that would end below the minimum charge level without a grid charge. */
    private final Optional<LocalDate> gridDay;
    private final ChartMapper charts;

    /**
     * Makes the mapper for one plan.
     *
     * @param planned the plan whose time zone, day of making, battery assumption, grid advice, house use and charge
     *     levels the days are told and drawn in
     * @param messages the texts, in the owner's language
     */
    DayMapper(Outlook.Planned planned, Messages messages) {
        this.messages = messages;
        this.zone = planned.zone();
        this.today = planned.madeAt().atZone(zone).toLocalDate();
        this.todaysNote = planned.batteryAssumed()
                ? messages.text(TextKey.DAY_BATTERY_ASSUMED, messages.whole(planned.batteryPct().value())) : "";
        this.gridDay = planned.gridAdvice().map(GridAdvice::day);
        this.charts = new ChartMapper(planned, messages);
    }

    /**
     * The days in words.
     *
     * @param days the plan's days, today first; may be empty
     * @return one day model per day, in the same order
     */
    List<DayModel> map(List<DayPlan> days) {
        return days.stream().map(this::map).toList();
    }

    private DayModel map(DayPlan day) {
        return day.windows().isEmpty() ? withoutWindow(day) : withWindow(day);
    }

    private DayModel withWindow(DayPlan day) {
        var window = day.windows().getFirst();
        var title = messages.title(day.date(), today);
        var sunPct = messages.whole(window.solarShare() * 100);
        var facts = List.of(
                messages.text(TextKey.DAY_SET, window.amps(), messages.decimal(window.powerKw())),
                messages.text(TextKey.DAY_ENERGY, messages.decimal(window.solarKwh() + window.importKwh())));
        var chips = new ArrayList<Chip>();
        chips.add(new Chip(Source.SUN, messages.text(TextKey.DAY_SUN_SHARE, sunPct)));
        chips.add(battery(day));
        if (window.importKwh() >= NO_GRID_KWH) {
            chips.add(new Chip(Source.GRID, messages.text(TextKey.DAY_FROM_GRID, messages.decimal(window.importKwh()))));
        }
        var spoken = messages.text(TextKey.DAY_SPOKEN, title, messages.time(window.start(), zone),
                messages.time(window.end(), zone), window.amps(), sunPct);
        var note = sentences(laterWindows(day), noteFor(day));
        return new DayModel(day.date(), messages.tab(day.date(), today), title, DayKind.SUN, messages.text(TextKey.DAY_WINDOW),
                span(window), facts, chips, note, spoken, charts.curve(day), charts.battery(day));
    }

    private DayModel withoutWindow(DayPlan day) {
        if (gridDay.filter(day.date()::equals).isPresent()) {
            return withoutWindow(day, DayKind.GRID, messages.text(TextKey.DAY_GRID), messages.text(TextKey.DAY_GRID_WHY));
        }
        return withoutWindow(day, DayKind.NONE, messages.text(TextKey.DAY_NONE), messages.text(TextKey.DAY_NONE_WHY));
    }

    private DayModel withoutWindow(DayPlan day, DayKind kind, String heading, String why) {
        var title = messages.title(day.date(), today);
        var spoken = title + ": " + heading.toLowerCase(messages.locale()) + ". " + why;
        return new DayModel(day.date(), messages.tab(day.date(), today), title, kind, heading, "", List.of(), List.of(battery(day)),
                sentences(why, noteFor(day)), spoken, charts.curve(day), charts.battery(day));
    }

    private Chip battery(DayPlan day) {
        return new Chip(Source.CAR, messages.text(TextKey.DAY_BATTERY, messages.whole(day.socStartPct().value()),
                messages.whole(day.socAfterChargingPct().value())));
    }

    private String laterWindows(DayPlan day) {
        var later = day.windows().subList(1, day.windows().size());
        if (later.isEmpty()) {
            return "";
        }
        return messages.text(TextKey.DAY_LATER_WINDOWS, later.stream()
                .map(window -> messages.text(TextKey.DAY_LATER_WINDOW, span(window), window.amps()))
                .collect(Collectors.joining(", ")));
    }

    private String noteFor(DayPlan day) {
        return day.date().equals(today) ? todaysNote : "";
    }

    private String span(Window window) {
        return messages.span(window.start(), window.end(), zone);
    }

    private static String sentences(String... sentences) {
        return Stream.of(sentences).filter(sentence -> !sentence.isEmpty()).collect(Collectors.joining(" "));
    }
}
