package io.github.enadeag.carchargeplanner.engine.config;

/**
 * What the engine's config file holds: the description of the system, the planning preferences and the chosen
 * forecast providers.
 *
 * @param system the owner's home and equipment
 * @param planning the owner's planning preferences
 * @param forecast the chosen forecast providers and their settings that are not secret
 */
public record EngineConfig(HomeSystem system, PlanningPreferences planning, ForecastSelection forecast) {
}
