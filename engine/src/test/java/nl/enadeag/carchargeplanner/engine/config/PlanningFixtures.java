package nl.enadeag.carchargeplanner.engine.config;

/** Planning preferences for the tests, made from others. */
public final class PlanningFixtures {

    private PlanningFixtures() {
    }

    /** The same preferences with other charging hours. */
    public static PlanningPreferences withChargingHours(PlanningPreferences preferences, ChargingHours hours) {
        return new PlanningPreferences(preferences.minimumPct(), preferences.targetPct(), preferences.margin(),
                preferences.importToleranceKwh(), preferences.minWindow(), preferences.notifyLead(), hours);
    }
}
