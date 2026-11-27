package nl.enadeag.carchargeplanner.engine.config;

import nl.enadeag.carchargeplanner.engine.setting.ConfigSetting;
import nl.enadeag.carchargeplanner.engine.setting.InvalidSetting;
import java.time.Duration;

/**
 * The owner's choices for how the plan is made.
 *
 * @param minimumPct the minimum charge level of the battery, below {@code targetPct}
 * @param targetPct the target charge level of the battery, above {@code minimumPct}
 * @param margin the share of the forecast surplus to count on, more than {@link #MARGIN_ABOVE} and at most
 *     {@link #MAX_MARGIN}
 * @param importToleranceKwh the grid energy in kWh a charge window may take, {@link #MIN_IMPORT_TOLERANCE_KWH} or
 *     more
 * @param minWindow the shortest charge window, in whole minutes, more than {@link #MIN_WINDOW_ABOVE_MINUTES}
 * @param notifyLead how long before a charge window the owner is told, in whole minutes, 0 or more
 * @param chargingHours the hours in which the owner can start a charge
 */
public record PlanningPreferences(
        Percent minimumPct,
        Percent targetPct,
        double margin,
        double importToleranceKwh,
        Duration minWindow,
        Duration notifyLead,
        ChargingHours chargingHours) {

    /** The margin is more than this: a plan that counts on none of the surplus has nothing to plan with. */
    public static final int MARGIN_ABOVE = 0;

    /** The highest margin, the whole forecast surplus. */
    public static final int MAX_MARGIN = 1;

    /** The lowest import tolerance, in kWh: no grid energy at all. There is no highest. */
    public static final int MIN_IMPORT_TOLERANCE_KWH = 0;

    /** The shortest charge window is more than this many minutes. There is no highest. */
    public static final int MIN_WINDOW_ABOVE_MINUTES = 0;

    /**
     * What a new owner starts with: charge to 80 % and never below 30 %, count on 85 % of the surplus, take up to
     * 0.2 kWh from the grid per window, windows of at least 45 minutes, an announcement 15 minutes ahead, and the
     * {@link ChargingHours#STANDARD} charging hours.
     */
    public static final PlanningPreferences RECOMMENDED = new PlanningPreferences(new Percent(30), new Percent(80),
            0.85, 0.2, Duration.ofMinutes(45), Duration.ofMinutes(15));

    /**
     * Checks the values.
     *
     * @throws InvalidSetting when a value is outside the range given for it, or the minimum is not below the target
     * @throws IllegalArgumentException when a duration is not a whole number of minutes, which the config file
     *     cannot hold
     */
    public PlanningPreferences {
        if (minimumPct.value() >= targetPct.value()) {
            throw new InvalidSetting(ConfigSetting.MINIMUM_CHARGE, InvalidSetting.Rule.NOT_BELOW_TARGET,
                    "minimumPct and targetPct must satisfy 0 <= minimumPct < targetPct <= 100");
        }
        if (margin <= MARGIN_ABOVE || margin > MAX_MARGIN || !Double.isFinite(margin)) {
            throw new InvalidSetting(ConfigSetting.PLANNING_MARGIN, InvalidSetting.Rule.OUT_OF_RANGE,
                    "margin must be more than " + MARGIN_ABOVE + " and at most " + MAX_MARGIN);
        }
        if (importToleranceKwh < MIN_IMPORT_TOLERANCE_KWH || !Double.isFinite(importToleranceKwh)) {
            throw new InvalidSetting(ConfigSetting.IMPORT_TOLERANCE, InvalidSetting.Rule.OUT_OF_RANGE,
                    "importToleranceKwh must not be negative");
        }
        if (minWindow.compareTo(Duration.ofMinutes(MIN_WINDOW_ABOVE_MINUTES)) <= 0) {
            throw new InvalidSetting(ConfigSetting.MINIMUM_WINDOW, InvalidSetting.Rule.OUT_OF_RANGE,
                    "minWindowMinutes must be more than " + MIN_WINDOW_ABOVE_MINUTES);
        }
        if (notifyLead.isNegative()) {
            throw new InvalidSetting(ConfigSetting.NOTIFY_LEAD, InvalidSetting.Rule.OUT_OF_RANGE,
                    "notifyLeadMinutes must not be negative");
        }
        if (minWindow.toSecondsPart() != 0 || minWindow.toNanosPart() != 0) {
            throw new IllegalArgumentException("minWindow must be whole minutes, not " + minWindow);
        }
        if (notifyLead.toSecondsPart() != 0 || notifyLead.toNanosPart() != 0) {
            throw new IllegalArgumentException("notifyLead must be whole minutes, not " + notifyLead);
        }
    }

    /**
     * Preferences with the {@link ChargingHours#STANDARD} charging hours.
     *
     * @param minimumPct the minimum charge level of the battery, below {@code targetPct}
     * @param targetPct the target charge level of the battery, above {@code minimumPct}
     * @param margin the share of the forecast surplus to count on, more than 0 and at most 1
     * @param importToleranceKwh the grid energy in kWh a charge window may take, 0 or more
     * @param minWindow the shortest charge window, in whole minutes, more than 0
     * @param notifyLead how long before a charge window the owner is told, in whole minutes, 0 or more
     * @throws InvalidSetting when a value is outside the range given for it, or the minimum is not below the target
     * @throws IllegalArgumentException when a duration is not a whole number of minutes
     */
    public PlanningPreferences(Percent minimumPct, Percent targetPct, double margin, double importToleranceKwh,
            Duration minWindow, Duration notifyLead) {
        this(minimumPct, targetPct, margin, importToleranceKwh, minWindow, notifyLead, ChargingHours.STANDARD);
    }
}
