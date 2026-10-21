package nl.enadeag.carchargeplanner.desktop.dashboard.battery;

import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.desktop.text.TextKey;
import nl.enadeag.carchargeplanner.engine.Outlook;
import nl.enadeag.carchargeplanner.engine.plan.DayPlan;
import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The days of a plan as the battery bar draws them: the levels, with the minimum and the target, and on a day a trip
 * leaves the level the trip leaves at the end of the day.
 */
public final class BatteryMapper {

    private final Outlook.Planned planned;
    private final Messages messages;
    private final LocalDate today;
    private final Set<LocalDate> departures;

    /**
     * Makes the mapper for one plan.
     *
     * @param planned the plan whose day of making, battery assumption and charge levels the bars show
     * @param messages the texts and numbers, in the owner's language
     */
    public BatteryMapper(Outlook.Planned planned, Messages messages) {
        this.planned = planned;
        this.messages = messages;
        this.today = planned.madeAt().atZone(planned.zone()).toLocalDate();
        this.departures = planned.tripAdvice().stream()
                .map(advice -> advice.trip().departure().atZone(planned.zone()).toLocalDate())
                .collect(Collectors.toSet());
    }

    /**
     * A day's battery: its level at the start and after charging, with the minimum and target; on a day a trip
     * leaves, also the level at the end of the day, after the trip.
     *
     * @param day one of the plan's days
     * @return the bar's data; only today's level can be entered
     */
    public BatteryModel map(DayPlan day) {
        double level = day.startLevelPct().value();
        double after = day.levelAfterChargingPct().value();
        double minimum = planned.minimumPct().value();
        double target = planned.targetPct().value();
        boolean isToday = day.date().equals(today);
        boolean departs = departures.contains(day.date());
        var spoken = isToday
                ? sentences(messages.text(TextKey.BATTERY_SPOKEN_TODAY, pct(level), pct(after), pct(minimum), pct(target)),
                        planned.batteryAssumed() ? messages.text(TextKey.BATTERY_ASSUMED) : "")
                : messages.text(TextKey.BATTERY_SPOKEN_DAY, pct(level), messages.inSentence(day.date(), today),
                        pct(after), pct(minimum), pct(target));
        var end = pct(day.endLevelPct().value());
        if (departs) {
            spoken = sentences(spoken, messages.text(TextKey.BATTERY_SPOKEN_AFTER_TRIP, end));
        }
        return new BatteryModel(level, after, minimum, target, levels(level, after, end, departs),
                messages.text(TextKey.BATTERY_MINIMUM, pct(minimum)), messages.text(TextKey.BATTERY_TARGET, pct(target)),
                isToday, spoken);
    }

    /** Without a charge before the trip, the level after charging is the start's, and is left out. */
    private String levels(double level, double after, String end, boolean departs) {
        if (!departs) {
            return messages.text(TextKey.BATTERY_LEVELS, pct(level), pct(after));
        }
        return pct(level).equals(pct(after)) ? messages.text(TextKey.BATTERY_LEVELS_TRIP, pct(level), end)
                : messages.text(TextKey.BATTERY_LEVELS_CHARGE_TRIP, pct(level), pct(after), end);
    }

    private String pct(double value) {
        return messages.whole(value);
    }

    private static String sentences(String... sentences) {
        return Stream.of(sentences).filter(sentence -> !sentence.isEmpty()).collect(Collectors.joining(" "));
    }
}
