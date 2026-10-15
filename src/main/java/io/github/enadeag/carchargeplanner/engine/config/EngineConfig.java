package io.github.enadeag.carchargeplanner.engine.config;

import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
     * The config set-up starts from when there is none yet: one panel array, a house, a car and a charger with
     * typical values, all tagged as such; no place chosen yet; the recommended planning preferences; one forecast
     * provider without a fallback; and no away periods.
     *
     * @param timezone the time zone the home is assumed to be in until the owner chooses a place, such as the
     *     computer's own
     * @param primary the forecast provider to ask, normally the first one this version offers
     * @return a draft that can be planned with, but whose place still has to be chosen
     */
    public static EngineConfig firstRun(ZoneId timezone, ProviderId primary) {
        var system = new HomeSystem(Coordinates.NOT_CHOSEN, timezone, List.of(PanelArray.TYPICAL), House.TYPICAL,
                Car.TYPICAL, Charger.TYPICAL);
        return new EngineConfig(system, PlanningPreferences.RECOMMENDED,
                new ForecastSelection(primary, Optional.empty(), Map.of()), AwaySchedule.NONE,
                new TypicalValues(TypicalValues.SYSTEM_SETTINGS, List.of(TypicalValues.PANEL_SETTINGS)));
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

    /**
     * The config with another choice of forecast providers.
     *
     * @param newForecast the providers and their settings that are not secret
     * @return a config with everything else the same
     */
    public EngineConfig withForecast(ForecastSelection newForecast) {
        return new EngineConfig(system, planning, newForecast, away, typical);
    }

    /**
     * The config with other weekly away periods.
     *
     * @param newAway the away periods that come back every week
     * @return a config with everything else the same
     */
    public EngineConfig withAway(AwaySchedule newAway) {
        return new EngineConfig(system, planning, forecast, newAway, typical);
    }
}
