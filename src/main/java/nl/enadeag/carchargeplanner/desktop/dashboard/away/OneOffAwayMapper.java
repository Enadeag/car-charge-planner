package nl.enadeag.carchargeplanner.desktop.dashboard.away;

import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.desktop.text.TextKey;
import nl.enadeag.carchargeplanner.engine.away.OneOffAwayPeriod;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

/** The away periods entered for specific days, as the dashboard lists them, in the time zone of the owner's home. */
public final class OneOffAwayMapper {

    private final Messages messages;
    private final ZoneId zone;
    private final Instant madeAt;

    /**
     * Makes the mapper for one plan.
     *
     * @param zone the time zone of the owner's home, in which the days and times are told
     * @param madeAt the moment the plan was made; periods that ended by then are left out
     * @param messages the texts, in the owner's language
     */
    public OneOffAwayMapper(ZoneId zone, Instant madeAt, Messages messages) {
        this.messages = messages;
        this.zone = zone;
        this.madeAt = madeAt;
    }

    /**
     * The periods that have not ended, in words.
     *
     * @param periods the stored periods, in order of their start; may be empty
     * @return the model with those that end after the plan was made, in the same order
     */
    public OneOffAwayModel map(List<OneOffAwayPeriod> periods) {
        var coming = periods.stream()
                .filter(period -> period.end().isAfter(madeAt))
                .map(period -> {
                    var text = describe(period);
                    return new OneOffAwayModel.Listed(period, text, messages.text(TextKey.ONE_OFF_REMOVE_SPOKEN, text));
                })
                .toList();
        return new OneOffAwayModel(zone, madeAt, coming);
    }

    private String describe(OneOffAwayPeriod period) {
        var today = madeAt.atZone(zone).toLocalDate();
        var start = period.start().atZone(zone);
        var end = period.end().atZone(zone);
        var day = messages.date(start.toLocalDate(), today);
        if (period.equals(OneOffAwayPeriod.allDay(start.toLocalDate(), zone))) {
            return messages.text(TextKey.ONE_OFF_ALL_DAY, day);
        }
        if (start.toLocalDate().equals(end.toLocalDate())) {
            return messages.text(TextKey.ONE_OFF_PART, day, messages.span(period.start(), period.end(), zone));
        }
        return messages.text(TextKey.ONE_OFF_DAYS, day, messages.time(period.start(), zone),
                messages.date(end.toLocalDate(), today), messages.time(period.end(), zone));
    }
}
