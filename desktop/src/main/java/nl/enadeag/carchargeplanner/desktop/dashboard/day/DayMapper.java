package nl.enadeag.carchargeplanner.desktop.dashboard.day;

import nl.enadeag.carchargeplanner.desktop.dashboard.battery.BatteryMapper;
import nl.enadeag.carchargeplanner.desktop.dashboard.parts.ChipModel;
import nl.enadeag.carchargeplanner.desktop.dashboard.parts.EnergySource;
import nl.enadeag.carchargeplanner.desktop.dashboard.parts.DayKind;
import nl.enadeag.carchargeplanner.desktop.dashboard.solar.SolarChartMapper;
import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.desktop.text.TextKey;
import nl.enadeag.carchargeplanner.engine.Outlook;
import nl.enadeag.carchargeplanner.engine.plan.DayPlan;
import nl.enadeag.carchargeplanner.engine.plan.GridAdvice;
import nl.enadeag.carchargeplanner.engine.plan.Window;
import nl.enadeag.carchargeplanner.engine.trip.Trip;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** The days of a plan as the dashboard's tabs show them, in the time zone of the owner's home. */
public final class DayMapper {

    /** Less grid energy than this is shown as none: it is within the forecast's precision. */
    private static final double NO_GRID_KWH = 0.05;

    private final Messages messages;
    private final ZoneId zone;
    private final LocalDate today;
    private final String todaysNote;
    /**
     * The day that would end below the minimum charge level without a grid charge; null when the plan gives no grid
     * advice.
     */
    private final LocalDate gridDay;
    /** The days a trip the plan charges for leaves on. */
    private final Set<LocalDate> departures;
    /** The trips made with this car, which keep it away on the days from their departure to their return. */
    private final List<Trip> trips;
    private final SolarChartMapper charts;
    private final BatteryMapper batteries;

    /**
     * Makes the mapper for one plan.
     *
     * @param planned the plan whose time zone, day of making, battery assumption, grid advice, trips, house use and
     *     charge levels the days are told and drawn in
     * @param trips the stored trips, also those under way and those made without this car; may be empty
     * @param messages the texts, in the owner's language
     */
    public DayMapper(Outlook.Planned planned, List<Trip> trips, Messages messages) {
        this.messages = messages;
        this.zone = planned.zone();
        this.today = planned.madeAt().atZone(zone).toLocalDate();
        this.todaysNote = planned.batteryAssumed()
                ? messages.text(TextKey.DAY_BATTERY_ASSUMED, messages.whole(planned.batteryLevelPct().value())) : "";
        this.gridDay = planned.gridAdvice().map(GridAdvice::day).orElse(null);
        this.departures = planned.tripAdvice().stream()
                .map(advice -> advice.trip().departure().atZone(zone).toLocalDate())
                .collect(Collectors.toSet());
        this.trips = trips.stream().filter(trip -> !trip.notWithThisCar()).toList();
        this.charts = new SolarChartMapper(planned, messages);
        this.batteries = new BatteryMapper(planned, messages);
    }

    /**
     * The days in words.
     *
     * @param days the plan's days, today first; may be empty
     * @return one day model per day, in the same order
     */
    public List<DayModel> map(List<DayPlan> days) {
        return days.stream().map(this::map).toList();
    }

    private DayModel map(DayPlan day) {
        return day.windows().isEmpty() ? withoutWindow(day) : withWindow(day);
    }

    private DayModel withWindow(DayPlan day) {
        var window = day.windows().getFirst();
        var title = messages.title(day.date(), today);
        var solarSharePct = messages.whole(window.solarShare() * 100);
        var facts = List.of(
                messages.text(TextKey.DAY_SET, window.amps(), messages.decimal(window.powerKw())),
                messages.text(TextKey.DAY_ENERGY, messages.decimal(window.solarKwh() + window.importKwh())));
        var chips = new ArrayList<ChipModel>();
        chips.add(new ChipModel(EnergySource.SOLAR, messages.text(TextKey.DAY_SOLAR_SHARE, solarSharePct)));
        chips.add(batteryChip(day));
        if (window.importKwh() >= NO_GRID_KWH) {
            chips.add(new ChipModel(EnergySource.GRID,
                    messages.text(TextKey.DAY_FROM_GRID, messages.decimal(window.importKwh()))));
        }
        var spoken = messages.text(TextKey.DAY_SPOKEN, title, messages.time(window.start(), zone),
                messages.time(window.end(), zone), window.amps(), solarSharePct);
        var note = sentences(laterWindows(day), noteFor(day));
        return new DayModel(day.date(), messages.tab(day.date(), today), title, DayKind.SOLAR, messages.text(TextKey.DAY_WINDOW),
                span(window), facts, chips, note, spoken, charts.map(day), batteries.map(day));
    }

    /**
     * A day without a window says why: the grid advice, or a trip. The day a trip leaves says nothing, as the trip's
     * advice on its tab tells what the battery needs.
     */
    private DayModel withoutWindow(DayPlan day) {
        var none = messages.text(TextKey.DAY_NONE);
        if (day.date().equals(gridDay)) {
            return withoutWindow(day, DayKind.GRID, messages.text(TextKey.DAY_GRID), messages.text(TextKey.DAY_GRID_WHY));
        }
        if (departures.contains(day.date())) {
            return withoutWindow(day, DayKind.NONE, none, "");
        }
        if (awayOnATrip(day.date())) {
            return withoutWindow(day, DayKind.NONE, none, messages.text(TextKey.DAY_NONE_TRIP));
        }
        return withoutWindow(day, DayKind.NONE, none, messages.text(TextKey.DAY_NONE_WHY));
    }

    /** From the day of the departure to the day of the return; a return at midnight belongs to the day before. */
    private boolean awayOnATrip(LocalDate date) {
        return trips.stream().anyMatch(trip -> !date.isBefore(trip.departure().atZone(zone).toLocalDate())
                && !date.isAfter(trip.returnTime().minusNanos(1).atZone(zone).toLocalDate()));
    }

    private DayModel withoutWindow(DayPlan day, DayKind kind, String heading, String why) {
        var title = messages.title(day.date(), today);
        var spoken = sentences(title + ": " + heading.toLowerCase(messages.locale()) + ".", why);
        return new DayModel(day.date(), messages.tab(day.date(), today), title, kind, heading, "", List.of(), List.of(batteryChip(day)),
                sentences(why, noteFor(day)), spoken, charts.map(day), batteries.map(day));
    }

    /**
     * The levels at the start and after charging; on a day a trip leaves also the level the trip leaves at the end of
     * the day, and without a charge before it only the start and the end.
     */
    private ChipModel batteryChip(DayPlan day) {
        var start = messages.whole(day.startLevelPct().value());
        var after = messages.whole(day.levelAfterChargingPct().value());
        if (!departures.contains(day.date())) {
            return new ChipModel(EnergySource.CAR, messages.text(TextKey.DAY_BATTERY, start, after));
        }
        var end = messages.whole(day.endLevelPct().value());
        return new ChipModel(EnergySource.CAR, start.equals(after)
                ? messages.text(TextKey.DAY_BATTERY_TRIP, start, end)
                : messages.text(TextKey.DAY_BATTERY_CHARGE_TRIP, start, after, end));
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
