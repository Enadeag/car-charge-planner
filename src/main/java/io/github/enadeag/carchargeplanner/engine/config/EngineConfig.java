package io.github.enadeag.carchargeplanner.engine.config;

/**
 * What the engine's config file holds: the description of the system, the planning preferences, the chosen forecast
 * providers and the weekly away periods.
 *
 * @param system the owner's home and equipment
 * @param planning the owner's planning preferences
 * @param forecast the chosen forecast providers and their settings that are not secret
 * @param away the away periods that come back every week; {@link AwaySchedule#NONE} when the file has no such section
 */
public record EngineConfig(HomeSystem system, PlanningPreferences planning, ForecastSelection forecast,
        AwaySchedule away) {
}
