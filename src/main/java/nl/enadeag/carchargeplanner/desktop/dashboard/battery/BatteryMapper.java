package nl.enadeag.carchargeplanner.desktop.dashboard.battery;

import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.desktop.text.TextKey;
import nl.enadeag.carchargeplanner.engine.Outlook;
import nl.enadeag.carchargeplanner.engine.plan.DayPlan;
import java.time.LocalDate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** The days of a plan as the battery bar draws them: the levels, with the minimum and the target. */
public final class BatteryMapper {

    private final Outlook.Planned planned;
    private final Messages messages;
    private final LocalDate today;

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
    }

    /**
     * A day's battery: its level at the start and after charging, with the minimum and target.
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
        var spoken = isToday
                ? sentences(messages.text(TextKey.BATTERY_SPOKEN_TODAY, pct(level), pct(after), pct(minimum), pct(target)),
                        planned.batteryAssumed() ? messages.text(TextKey.BATTERY_ASSUMED) : "")
                : messages.text(TextKey.BATTERY_SPOKEN_DAY, pct(level), messages.inSentence(day.date(), today),
                        pct(after), pct(minimum), pct(target));
        return new BatteryModel(level, after, minimum, target,
                messages.text(TextKey.BATTERY_LEVELS, pct(level), pct(after)),
                messages.text(TextKey.BATTERY_MINIMUM, pct(minimum)), messages.text(TextKey.BATTERY_TARGET, pct(target)),
                isToday, spoken);
    }

    private String pct(double value) {
        return messages.whole(value);
    }

    private static String sentences(String... sentences) {
        return Stream.of(sentences).filter(sentence -> !sentence.isEmpty()).collect(Collectors.joining(" "));
    }
}
