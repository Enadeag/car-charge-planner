package nl.enadeag.carchargeplanner.desktop.dashboard.away;

import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.desktop.text.TextKey;
import nl.enadeag.carchargeplanner.engine.dashboard.ComingAway;
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
     * @param zone the time zone of the owner's home, in which the times are told
     * @param madeAt the moment the plan was made
     * @param messages the texts, in the owner's language
     */
    public OneOffAwayMapper(ZoneId zone, Instant madeAt, Messages messages) {
        this.messages = messages;
        this.zone = zone;
        this.madeAt = madeAt;
    }

    /**
     * The coming periods in words.
     *
     * @param coming the periods that had not ended when the plan was made, as the engine's dashboard lists them, in
     *     their order; may be empty
     * @return the model with those periods, in the same order
     */
    public OneOffAwayModel map(List<ComingAway> coming) {
        var listed = coming.stream()
                .map(away -> {
                    var text = describe(away);
                    return new OneOffAwayModel.Listed(away.period(), text,
                            messages.text(TextKey.ONE_OFF_REMOVE_SPOKEN, text));
                })
                .toList();
        return new OneOffAwayModel(zone, madeAt, listed);
    }

    private String describe(ComingAway away) {
        return switch (away) {
            case ComingAway.AllDay allDay -> messages.text(TextKey.ONE_OFF_ALL_DAY, messages.date(allDay.day()));
            case ComingAway.PartOfDay part -> messages.text(TextKey.ONE_OFF_PART, messages.date(part.day()),
                    messages.span(part.start(), part.end(), zone));
            case ComingAway.Days days -> messages.text(TextKey.ONE_OFF_DAYS, messages.date(days.startDay()),
                    messages.time(days.start(), zone), messages.date(days.endDay()), messages.time(days.end(), zone));
        };
    }
}
