package nl.enadeag.carchargeplanner.desktop.settings.form;

import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.desktop.text.TextKey;
import nl.enadeag.carchargeplanner.engine.away.WeeklyAwayPeriod;
import nl.enadeag.carchargeplanner.engine.setting.InvalidSetting;
import java.time.DayOfWeek;

/**
 * A weekly away period as its row shows it: a weekday and the two times as typed.
 *
 * @param weekday the day the period is on
 * @param start the start as typed, such as "08:00"
 * @param end the end as typed, such as "17:00"
 */
public record AwayPeriodEntry(DayOfWeek weekday, String start, String end) {

    /** The period as entered, or what is wrong with it. */
    public sealed interface Checked {

        /**
         * The period's record accepted it.
         *
         * @param period the period
         */
        record Accepted(WeeklyAwayPeriod period) implements Checked {
        }

        /**
         * A time is not a time, or the record refused the period.
         *
         * @param problem what the row says, in the owner's language
         */
        record Refused(String problem) implements Checked {
        }
    }

    /**
     * Has the period's record check the entry.
     *
     * @param messages the texts, in the owner's language
     * @return the period, or the problem the row shows
     */
    public Checked check(Messages messages) {
        var from = TimeText.read(start);
        var to = TimeText.read(end);
        if (from.isEmpty() || to.isEmpty()) {
            return new Checked.Refused(messages.text(TextKey.PROBLEM_NOT_A_TIME));
        }
        try {
            return new Checked.Accepted(new WeeklyAwayPeriod(weekday, from.get(), to.get()));
        } catch (InvalidSetting e) {
            return new Checked.Refused(ProblemTexts.of(e.rule(), messages));
        }
    }
}
