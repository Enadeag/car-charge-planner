package nl.enadeag.carchargeplanner.desktop.dashboard.away;

import nl.enadeag.carchargeplanner.engine.away.OneOffAwayPeriod;
import java.time.ZoneId;
import java.util.List;

/**
 * The away periods the owner entered for specific days that have not ended yet, in words, with the time zone of the
 * owner's home.
 *
 * @param zone the time zone of the owner's home
 * @param coming the periods that had not ended when the plan was made, in their stored order; may be empty
 */
public record OneOffAwayModel(ZoneId zone, List<Listed> coming) {

    /**
     * Keeps a copy of the periods, so the model cannot change afterwards.
     */
    public OneOffAwayModel {
        coming = List.copyOf(coming);
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
