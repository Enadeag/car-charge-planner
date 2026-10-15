package io.github.enadeag.carchargeplanner.engine.config;

/**
 * What the engine's config file holds: the description of the system, the planning preferences, the chosen forecast
 * providers, the weekly away periods, and which of the system's values are typical values.
 *
 * @param system the owner's home and equipment
 * @param planning the owner's planning preferences
 * @param forecast the chosen forecast providers and their settings that are not secret
 * @param away the away periods that come back every week; {@link AwaySchedule#NONE} when the file has no such section
 * @param typical the system's values that are typical values; {@link TypicalValues#NONE} when the file has no such
 *     section
 */
public record EngineConfig(HomeSystem system, PlanningPreferences planning, ForecastSelection forecast,
        AwaySchedule away, TypicalValues typical) {

    /**
     * Checks that the typical values are about panel arrays the system has.
     *
     * @throws IllegalArgumentException when the typical values list more panel arrays than the system has
     */
    public EngineConfig {
        if (typical.panels().size() > system.panels().size()) {
            throw new IllegalArgumentException("typical.panels lists more panel arrays than system.panels has");
        }
    }

    /**
     * The config with another system and its typical values.
     *
     * @param newSystem the system to keep
     * @param newTypical which of its values are typical values
     * @return a config with the same planning, forecast and away periods
     */
    public EngineConfig withSystem(HomeSystem newSystem, TypicalValues newTypical) {
        return new EngineConfig(newSystem, planning, forecast, away, newTypical);
    }

    /**
     * The config with other planning preferences.
     *
     * @param newPlanning the preferences to keep
     * @return a config with everything else the same
     */
    public EngineConfig withPlanning(PlanningPreferences newPlanning) {
        return new EngineConfig(system, newPlanning, forecast, away, typical);
    }
}
