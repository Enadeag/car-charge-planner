package io.github.enadeag.carchargeplanner.desktop;

import static io.github.enadeag.carchargeplanner.desktop.Format.text;

import io.github.enadeag.carchargeplanner.desktop.Chip.Source;
import io.github.enadeag.carchargeplanner.engine.Outlook;
import io.github.enadeag.carchargeplanner.engine.plan.DayPlan;
import io.github.enadeag.carchargeplanner.engine.plan.Window;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** The days of a plan as the dashboard's tabs show them, in the time zone of the owner's home. */
final class DayMapper {

    /** Less grid energy than this is shown as none: it is within the forecast's precision. */
    private static final double NO_GRID_KWH = 0.05;

    private final ZoneId zone;
    private final LocalDate today;
    private final String todaysNote;

    DayMapper(Outlook.Planned planned) {
        this.zone = planned.zone();
        this.today = planned.madeAt().atZone(zone).toLocalDate();
        this.todaysNote = planned.batteryAssumed()
                ? text("The plan assumes the battery is at %.0f %%.", planned.batteryPct()) : "";
    }

    List<DayModel> map(List<DayPlan> days) {
        return days.stream().map(this::map).toList();
    }

    private DayModel map(DayPlan day) {
        return day.windows().isEmpty() ? withoutWindow(day) : withWindow(day);
    }

    private DayModel withWindow(DayPlan day) {
        var window = day.windows().getFirst();
        var title = Format.title(day.date(), today);
        long sunPct = Math.round(window.solarShare() * 100);
        var facts = List.of(
                text("Set %d A (%.1f kW)", window.amps(), window.powerKw()),
                text("about %.1f kWh", window.solarKwh() + window.importKwh()));
        var chips = new ArrayList<Chip>();
        chips.add(new Chip(Source.SUN, text("%d %% sun", sunPct)));
        chips.add(battery(day));
        if (window.importKwh() >= NO_GRID_KWH) {
            chips.add(new Chip(Source.GRID, text("%.1f kWh from the grid", window.importKwh())));
        }
        var spoken = text("%s: charge window %s to %s, %d A, %d %% sun.", title,
                Format.time(window.start(), zone), Format.time(window.end(), zone), window.amps(), sunPct);
        var note = sentences(laterWindows(day), noteFor(day));
        return new DayModel(Format.tab(day.date(), today), title, DayKind.SUN, "Charge window", span(window),
                facts, chips, note, spoken);
    }

    private DayModel withoutWindow(DayPlan day) {
        var title = Format.title(day.date(), today);
        var why = "The car needs no charging, or there is too little sun for it.";
        return new DayModel(Format.tab(day.date(), today), title, DayKind.NONE, "No charge window", "",
                List.of(), List.of(battery(day)), sentences(why, noteFor(day)), title + ": no charge window. " + why);
    }

    private static Chip battery(DayPlan day) {
        return new Chip(Source.CAR, text("Battery %.0f → %.0f %%", day.socStartPct(), day.socAfterChargingPct()));
    }

    private String laterWindows(DayPlan day) {
        var later = day.windows().subList(1, day.windows().size());
        if (later.isEmpty()) {
            return "";
        }
        return later.stream().map(window -> span(window) + " at " + window.amps() + " A")
                .collect(Collectors.joining(", ", "Also ", "."));
    }

    private String noteFor(DayPlan day) {
        return day.date().equals(today) ? todaysNote : "";
    }

    private String span(Window window) {
        return Format.time(window.start(), zone) + " – " + Format.time(window.end(), zone);
    }

    private static String sentences(String... sentences) {
        return Stream.of(sentences).filter(sentence -> !sentence.isEmpty()).collect(Collectors.joining(" "));
    }
}
