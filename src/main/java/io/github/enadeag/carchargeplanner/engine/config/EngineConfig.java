package io.github.enadeag.carchargeplanner.engine.config;

/** What the engine's config file holds: the description of the system and the planning preferences. */
public record EngineConfig(HomeSystem system, PlanningPreferences planning) {
}
