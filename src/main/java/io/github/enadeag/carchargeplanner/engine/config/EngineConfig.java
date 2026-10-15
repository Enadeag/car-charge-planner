package io.github.enadeag.carchargeplanner.engine.config;

/**
 * What the engine's config file holds: the description of the system, the planning preferences and the chosen
 * forecast providers.
 */
public record EngineConfig(HomeSystem system, PlanningPreferences planning, ForecastSelection forecast) {
}
