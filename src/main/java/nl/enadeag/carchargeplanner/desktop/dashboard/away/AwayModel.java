package nl.enadeag.carchargeplanner.desktop.dashboard.away;

import nl.enadeag.carchargeplanner.engine.away.OneOffAwayPeriod;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * The away periods the owner entered for specific days that have not ended yet, in words, with the time zone and the
 * moment of the plan they belong to.
 *
 * @param zone the time zone of the owner's home
 * @param now the moment the plan was made
 * @param coming the periods that had not ended at {@code now}, in order of their start; may be empty
 */
public record AwayModel(ZoneId zone, Instant now, List<Listed> coming) {

    /**
     * Keeps a copy of the periods, so the model cannot change afterwards.
     *
     * @param zone the time zone of the owner's home
     * @param now the moment the plan was made
     * @param coming the periods that had not ended at {@code now}, in order of their start; may be empty
     */
    public AwayModel {
        coming = List.copyOf(coming);
    }

    /**
     * The day it is in the owner's home.
     *
     * @return the local date of {@code now}
     */
    LocalDate today() {
        return now.atZone(zone).toLocalDate();
    }

    /**
     * One away period in words.
     *
     * @param period the period as the engine stores it
     * @param text the period, such as "Tomorrow, all day" or "Sat 17 Oct, 10:00 – 16:30"
     * @param removeSpoken what a screen reader calls its remove button, such as "Remove Tomorrow, all day"
     */
    public record Listed(OneOffAwayPeriod period, String text, String removeSpoken) {
    }
}
