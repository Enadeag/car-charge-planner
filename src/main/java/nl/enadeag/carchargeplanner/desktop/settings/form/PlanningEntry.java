package nl.enadeag.carchargeplanner.desktop.settings.form;

import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.desktop.text.TextKey;
import nl.enadeag.carchargeplanner.engine.config.ChargingHours;
import nl.enadeag.carchargeplanner.engine.config.Percent;
import nl.enadeag.carchargeplanner.engine.config.PlanningPreferences;
import nl.enadeag.carchargeplanner.engine.setting.ConfigSetting;
import nl.enadeag.carchargeplanner.engine.setting.InvalidSetting;
import java.time.Duration;

/**
 * The planning preferences as their page shows them: the margin as a percentage, the durations in minutes, the
 * charging hours as typed.
 *
 * @param minimumPct the minimum charge level, in %
 * @param targetPct the target charge level, in %
 * @param marginPct the share of the forecast surplus to count on, in %
 * @param importToleranceKwh the grid energy a charge window may take, in kWh
 * @param minWindowMinutes the shortest charge window, in minutes
 * @param notifyLeadMinutes how long before a charge window the owner is told, in minutes
 * @param chargingStart the start of the charging hours as typed, such as "07:00"
 * @param chargingEnd the end of the charging hours as typed, such as "22:00"
 */
public record PlanningEntry(double minimumPct, double targetPct, double marginPct, double importToleranceKwh,
        double minWindowMinutes, double notifyLeadMinutes, String chargingStart, String chargingEnd) {

    /** The preferences as entered, or what is wrong with them. */
    public sealed interface Checked {

        /**
         * The preferences' record accepted them.
         *
         * @param planning the preferences
         */
        record Accepted(PlanningPreferences planning) implements Checked {
        }

        /**
         * A time is not a time, or a record refused a value.
         *
         * @param setting the setting whose field shows the problem
         * @param problem what the field says, in the owner's language
         */
        record Refused(ConfigSetting setting, String problem) implements Checked {
        }
    }

    /**
     * What the page shows for the planning preferences.
     *
     * @param planning the preferences as the engine keeps them
     * @return their values as the fields show them
     */
    public static PlanningEntry of(PlanningPreferences planning) {
        var hours = planning.chargingHours();
        return new PlanningEntry(planning.minimumPct().value(), planning.targetPct().value(), planning.margin() * 100,
                planning.importToleranceKwh(), planning.minWindow().toMinutes(), planning.notifyLead().toMinutes(),
                TimeText.write(hours.start()), TimeText.write(hours.end()));
    }

    /**
     * Has the preferences' records check the entry, with the durations rounded to whole minutes.
     *
     * @param messages the texts, in the owner's language
     * @return the preferences, or the problem and the field that shows it
     * @throws IllegalArgumentException when a charge level is not from 0 to 100, which its field does not allow
     */
    public Checked check(Messages messages) {
        var start = TimeText.read(chargingStart);
        var end = TimeText.read(chargingEnd);
        if (start.isEmpty() || end.isEmpty()) {
            return new Checked.Refused(ConfigSetting.CHARGING_HOURS, messages.text(TextKey.PROBLEM_NOT_A_TIME));
        }
        try {
            return new Checked.Accepted(new PlanningPreferences(new Percent(minimumPct), new Percent(targetPct),
                    marginPct / 100, importToleranceKwh, Duration.ofMinutes(Math.round(minWindowMinutes)),
                    Duration.ofMinutes(Math.round(notifyLeadMinutes)), new ChargingHours(start.get(), end.get())));
        } catch (InvalidSetting e) {
            return new Checked.Refused(e.setting(), ProblemTexts.of(e.rule(), messages));
        }
    }
}
