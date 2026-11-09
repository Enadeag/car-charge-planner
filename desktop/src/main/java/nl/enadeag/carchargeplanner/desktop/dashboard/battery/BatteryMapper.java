package nl.enadeag.carchargeplanner.desktop.dashboard.battery;

import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.desktop.text.TextKey;
import nl.enadeag.carchargeplanner.engine.dashboard.BatteryLevels;
import nl.enadeag.carchargeplanner.engine.dashboard.ChargeLevels;
import nl.enadeag.carchargeplanner.engine.dashboard.DashboardDay;
import nl.enadeag.carchargeplanner.engine.dashboard.DayName;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The days of a plan as the battery bar draws them, in words: the levels, with the minimum and the target, and on a
 * day a trip leaves the level the trip leaves at the end of the day.
 */
public final class BatteryMapper {

    private final Messages messages;

    /**
     * Makes the mapper.
     *
     * @param messages the texts and numbers, in the owner's language
     */
    public BatteryMapper(Messages messages) {
        this.messages = messages;
    }

    /**
     * A day's battery: its level at the start and after charging, with the minimum and target; on a day a trip
     * leaves, also the level at the end of the day, after the trip.
     *
     * @param day one of the dashboard's days, with its name and battery levels
     * @param levels the plan's minimum and target charge levels
     * @return the bar's data; only today's level can be entered
     */
    public BatteryModel map(DashboardDay day, ChargeLevels levels) {
        var battery = day.battery();
        double level = battery.startPct().value();
        double after = battery.afterPct().value();
        double minimum = levels.minimumPct().value();
        double target = levels.targetPct().value();
        boolean isToday = day.name() instanceof DayName.Today;
        var spoken = isToday
                ? sentences(messages.text(TextKey.BATTERY_SPOKEN_TODAY, pct(level), pct(after), pct(minimum), pct(target)),
                        day.assumedLevel().isPresent() ? messages.text(TextKey.BATTERY_ASSUMED) : "")
                : messages.text(TextKey.BATTERY_SPOKEN_DAY, pct(level), messages.inSentence(day.name()),
                        pct(after), pct(minimum), pct(target));
        if (battery.afterTripPct().isPresent()) {
            spoken = sentences(spoken,
                    messages.text(TextKey.BATTERY_SPOKEN_AFTER_TRIP, pct(battery.afterTripPct().get().value())));
        }
        return new BatteryModel(level, after, minimum, target, levels(battery),
                messages.text(TextKey.BATTERY_MINIMUM, pct(minimum)), messages.text(TextKey.BATTERY_TARGET, pct(target)),
                isToday, spoken);
    }

    /** Without a charge before the trip, the level after charging is the start's, and is left out. */
    private String levels(BatteryLevels battery) {
        var level = pct(battery.startPct().value());
        var after = pct(battery.afterPct().value());
        if (battery.afterTripPct().isEmpty()) {
            return messages.text(TextKey.BATTERY_LEVELS, level, after);
        }
        var end = pct(battery.afterTripPct().get().value());
        return battery.chargesBeforeTrip() ? messages.text(TextKey.BATTERY_LEVELS_CHARGE_TRIP, level, after, end)
                : messages.text(TextKey.BATTERY_LEVELS_TRIP, level, end);
    }

    private String pct(double value) {
        return messages.whole(value);
    }

    private static String sentences(String... sentences) {
        return Stream.of(sentences).filter(sentence -> !sentence.isEmpty()).collect(Collectors.joining(" "));
    }
}
