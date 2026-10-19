package nl.enadeag.carchargeplanner.desktop.settings.form;

import nl.enadeag.carchargeplanner.engine.config.Percent;
import nl.enadeag.carchargeplanner.engine.config.PlanningPreferences;
import java.time.Duration;

/**
 * The planning preferences as their page shows them: the margin as a percentage, the durations in minutes.
 *
 * @param minimumPct the minimum charge level, in %
 * @param targetPct the target charge level, in %
 * @param marginPct the share of the forecast surplus to count on, in %
 * @param importToleranceKwh the grid energy a charge window may take, in kWh
 * @param minWindowMinutes the shortest charge window, in minutes
 * @param notifyLeadMinutes how long before a charge window the owner is told, in minutes
 */
public record PlanningEntry(double minimumPct, double targetPct, double marginPct, double importToleranceKwh,
        double minWindowMinutes, double notifyLeadMinutes) {

    /**
     * What the page shows for the planning preferences.
     *
     * @param planning the preferences as the engine keeps them
     * @return their values as the fields show them
     */
    public static PlanningEntry of(PlanningPreferences planning) {
        return new PlanningEntry(planning.minimumPct().value(), planning.targetPct().value(), planning.margin() * 100,
                planning.importToleranceKwh(), planning.minWindow().toMinutes(), planning.notifyLead().toMinutes());
    }

    /**
     * The preferences as entered, with the durations rounded to whole minutes.
     *
     * @return the preferences, as their record checked them
     * @throws nl.enadeag.carchargeplanner.engine.setting.InvalidSetting when the record refuses a value
     * @throws IllegalArgumentException when a charge level is not from 0 to 100
     */
    public PlanningPreferences planning() {
        return new PlanningPreferences(new Percent(minimumPct), new Percent(targetPct), marginPct / 100,
                importToleranceKwh, Duration.ofMinutes(Math.round(minWindowMinutes)),
                Duration.ofMinutes(Math.round(notifyLeadMinutes)));
    }
}
