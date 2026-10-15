package io.github.enadeag.carchargeplanner.engine;

import io.github.enadeag.carchargeplanner.engine.config.ConfigFile;
import io.github.enadeag.carchargeplanner.engine.config.ConfigProblem;
import io.github.enadeag.carchargeplanner.engine.config.EngineConfig;
import io.github.enadeag.carchargeplanner.engine.forecast.ForecastProvider;
import io.github.enadeag.carchargeplanner.engine.forecast.ForecastStep;
import io.github.enadeag.carchargeplanner.engine.forecast.ProviderError;
import io.github.enadeag.carchargeplanner.engine.forecast.SystemDescription;
import io.github.enadeag.carchargeplanner.engine.plan.GridAdvisor;
import io.github.enadeag.carchargeplanner.engine.plan.Planner;
import java.time.Clock;
import java.util.List;
import java.util.Map;

/**
 * Plans, fetches forecasts and owns the stored data, without any user interface. Clients get plain records back.
 *
 * <p>Each call reads the config file again and fetches a new forecast; the forecast cache comes later.
 */
public final class Engine {

    /** Until the owner can enter the battery level, the plan starts from this one. */
    static final double ASSUMED_BATTERY_PCT = 50;

    private final ConfigFile configFile;
    private final ForecastProvider forecasts;
    private final Clock clock;

    public Engine(ConfigFile configFile, ForecastProvider forecasts, Clock clock) {
        this.configFile = configFile;
        this.forecasts = forecasts;
        this.clock = clock;
    }

    /** Blocks while the forecast is fetched: call it away from the user interface's thread. */
    public Outlook plan() {
        EngineConfig config;
        try {
            config = configFile.read();
        } catch (ConfigProblem e) {
            return new Outlook.NotSetUp(e.getMessage());
        }
        List<ForecastStep> forecast;
        try {
            forecast = forecasts.fetch(SystemDescription.of(config.system()), Map.of(), clock.instant());
        } catch (ProviderError e) {
            return new Outlook.NoForecast(e.getMessage());
        }
        var now = clock.instant();
        var system = config.system();
        // nothing is announced until there are notifications, so there is no announced window to keep
        var days = new Planner(system, config.planning()).plan(forecast, ASSUMED_BATTERY_PCT, now, List.of());
        var advice = new GridAdvisor(system, config.planning()).advise(days, forecast, now);
        return new Outlook.Planned(now, system.timezone(), days, ASSUMED_BATTERY_PCT, true, advice);
    }
}
