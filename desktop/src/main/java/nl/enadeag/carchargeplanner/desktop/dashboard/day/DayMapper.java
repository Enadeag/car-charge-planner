package nl.enadeag.carchargeplanner.desktop.dashboard.day;

import nl.enadeag.carchargeplanner.desktop.dashboard.battery.BatteryMapper;
import nl.enadeag.carchargeplanner.desktop.dashboard.parts.ChipModel;
import nl.enadeag.carchargeplanner.desktop.dashboard.solar.SolarChartMapper;
import nl.enadeag.carchargeplanner.desktop.dashboard.trip.TripAdviceModel;
import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.desktop.text.TextKey;
import nl.enadeag.carchargeplanner.engine.dashboard.BatteryLevels;
import nl.enadeag.carchargeplanner.engine.dashboard.ChargeLevels;
import nl.enadeag.carchargeplanner.engine.dashboard.DashboardDay;
import nl.enadeag.carchargeplanner.engine.dashboard.EnergySource;
import nl.enadeag.carchargeplanner.engine.dashboard.MainCharge;
import nl.enadeag.carchargeplanner.engine.plan.Window;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** The days of one plan as the dashboard's tabs show them, in words, in the time zone of the owner's home. */
public final class DayMapper {

    private final Messages messages;
    private final ZoneId zone;
    private final ChargeLevels levels;
    private final SolarChartMapper charts;
    private final BatteryMapper batteries;

    /**
     * Makes the mapper for one plan's days.
     *
     * @param messages the texts, in the owner's language; never null
     * @param zone the home's time zone, in which the times are told; never null
     * @param levels the plan's minimum and target, which the battery bars mark; never null
     */
    public DayMapper(Messages messages, ZoneId zone, ChargeLevels levels) {
        this.messages = messages;
        this.zone = zone;
        this.levels = levels;
        this.charts = new SolarChartMapper(zone, messages);
        this.batteries = new BatteryMapper(messages);
    }

    /**
     * A day in words, with the trip advice its tab shows and without grid advice or trips: the caller fills both
     * afterwards, the grid advice by {@link DayModel#withGridAdvice} and the trips by {@link DayModel#withTrips}.
     *
     * @param day one of the engine's days of the plan; never null
     * @param tripAdvice the advice of the trips the day's tab shows, in its words; may be empty, never null
     * @return the day model
     */
    public DayModel map(DashboardDay day, List<TripAdviceModel> tripAdvice) {
        return day.main().map(main -> withWindow(day, main, tripAdvice))
                .orElseGet(() -> withoutWindow(day, tripAdvice));
    }

    private DayModel withWindow(DashboardDay day, MainCharge main, List<TripAdviceModel> tripAdvice) {
        var window = main.window();
        var title = messages.title(day.name());
        var solarSharePct = messages.whole(window.solarSharePct().value());
        var facts = List.of(
                messages.text(TextKey.DAY_SET, window.amps(), messages.decimal(window.powerKw())),
                messages.text(TextKey.DAY_ENERGY, messages.decimal(window.energyKwh())));
        var chips = new ArrayList<ChipModel>();
        chips.add(new ChipModel(EnergySource.SOLAR, messages.text(TextKey.DAY_SOLAR_SHARE, solarSharePct)));
        chips.add(batteryChip(day.battery()));
        main.gridKwh().ifPresent(kwh -> chips.add(new ChipModel(EnergySource.GRID,
                messages.text(TextKey.DAY_FROM_GRID, messages.decimal(kwh)))));
        var spoken = messages.text(TextKey.DAY_SPOKEN, title, messages.time(window.start(), zone),
                messages.time(window.end(), zone), window.amps(), solarSharePct);
        var note = sentences(laterWindows(day.later()), assumedNote(day));
        return new DayModel(day.name().date(), messages.tab(day.name()), title, day.kind(),
                messages.text(TextKey.DAY_WINDOW), span(window), facts, chips, note, spoken, charts.map(day),
                batteries.map(day, levels), tripAdvice, Optional.empty(), List.of());
    }

    /**
     * The heading and the why come from the reason. The day a trip leaves says nothing, as the trip's advice on its tab
     * tells what the battery needs; the battery chip is there when the engine's day shows its battery.
     */
    private DayModel withoutWindow(DashboardDay day, List<TripAdviceModel> tripAdvice) {
        var reason = day.noWindow().orElseThrow();
        var heading = messages.text(switch (reason) {
            case GRID -> TextKey.DAY_GRID;
            case NOT_NEEDED -> TextKey.DAY_NOT_NEEDED;
            case DEPARTURE, AWAY, TOO_LITTLE_SOLAR -> TextKey.DAY_NONE;
        });
        var why = switch (reason) {
            case GRID -> messages.text(TextKey.DAY_GRID_WHY);
            case DEPARTURE, NOT_NEEDED -> "";
            case AWAY -> messages.text(TextKey.DAY_NONE_TRIP);
            case TOO_LITTLE_SOLAR -> messages.text(TextKey.DAY_TOO_LITTLE_SOLAR);
        };
        var chips = day.showsBattery() ? List.of(batteryChip(day.battery())) : List.<ChipModel>of();
        var title = messages.title(day.name());
        var spoken = sentences(title + ": " + heading.toLowerCase(messages.locale()) + ".", why);
        return new DayModel(day.name().date(), messages.tab(day.name()), title, day.kind(), heading, "", List.of(),
                chips, sentences(why, assumedNote(day)), spoken, charts.map(day), batteries.map(day, levels),
                tripAdvice, Optional.empty(), List.of());
    }

    /**
     * The levels at the start and after charging; on a day a trip leaves also the level the trip leaves at the
     * end of the day, and without a charge before it only the start and the end.
     */
    private ChipModel batteryChip(BatteryLevels battery) {
        var start = messages.whole(battery.startPct().value());
        var after = messages.whole(battery.afterPct().value());
        if (battery.afterTripPct().isEmpty()) {
            return new ChipModel(EnergySource.CAR, messages.text(TextKey.DAY_BATTERY, start, after));
        }
        var end = messages.whole(battery.afterTripPct().get().value());
        return new ChipModel(EnergySource.CAR, battery.chargesBeforeTrip()
                ? messages.text(TextKey.DAY_BATTERY_CHARGE_TRIP, start, after, end)
                : messages.text(TextKey.DAY_BATTERY_TRIP, start, end));
    }

    private String laterWindows(List<Window> later) {
        if (later.isEmpty()) {
            return "";
        }
        return messages.text(TextKey.DAY_LATER_WINDOWS, later.stream()
                .map(window -> messages.text(TextKey.DAY_LATER_WINDOW, span(window), window.amps()))
                .collect(Collectors.joining(", ")));
    }

    private String assumedNote(DashboardDay day) {
        return day.assumedLevel()
                .map(level -> messages.text(TextKey.DAY_BATTERY_ASSUMED, messages.whole(level.value())))
                .orElse("");
    }

    private String span(Window window) {
        return messages.span(window.start(), window.end(), zone);
    }

    private static String sentences(String... sentences) {
        return Stream.of(sentences).filter(sentence -> !sentence.isEmpty()).collect(Collectors.joining(" "));
    }
}
