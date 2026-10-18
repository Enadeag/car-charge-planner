package io.github.enadeag.carchargeplanner.desktop.dashboard;

import io.github.enadeag.carchargeplanner.desktop.text.Messages;
import io.github.enadeag.carchargeplanner.desktop.text.TextKey;
import io.github.enadeag.carchargeplanner.engine.away.AwayPeriod;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

/** The away periods entered for specific days, as the dashboard lists them, in the time zone of the owner's home. */
final class AwayMapper {

    private final Messages messages;
    private final ZoneId zone;
    private final Instant now;

    /**
     * Makes the mapper for one plan.
     *
     * @param zone the time zone of the owner's home, in which the days and times are told
     * @param now the moment the plan was made; periods that ended by then are left out
     * @param messages the texts, in the owner's language
     */
    AwayMapper(ZoneId zone, Instant now, Messages messages) {
        this.messages = messages;
        this.zone = zone;
        this.now = now;
    }

    /**
     * The periods that have not ended, in words.
     *
     * @param periods the stored periods, in order of their start; may be empty
     * @return the model with those that end after now, in the same order
     */
    AwayModel map(List<AwayPeriod> periods) {
        var coming = periods.stream()
                .filter(period -> period.end().isAfter(now))
                .map(period -> {
                    var text = describe(period);
                    return new AwayModel.Entry(period, text, messages.text(TextKey.ONE_OFF_REMOVE_SPOKEN, text));
                })
                .toList();
        return new AwayModel(zone, now, coming);
    }

    private String describe(AwayPeriod period) {
        var today = now.atZone(zone).toLocalDate();
        var start = period.start().atZone(zone);
        var end = period.end().atZone(zone);
        var day = messages.date(start.toLocalDate(), today);
        if (period.equals(AwayPeriod.allDay(start.toLocalDate(), zone))) {
            return messages.text(TextKey.ONE_OFF_ALL_DAY, day);
        }
        if (start.toLocalDate().equals(end.toLocalDate())) {
            return messages.text(TextKey.ONE_OFF_PART, day, messages.span(period.start(), period.end(), zone));
        }
        return messages.text(TextKey.ONE_OFF_DAYS, day, messages.time(period.start(), zone),
                messages.date(end.toLocalDate(), today), messages.time(period.end(), zone));
    }
}
