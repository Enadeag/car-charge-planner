package io.github.enadeag.carchargeplanner.engine;

import io.github.enadeag.carchargeplanner.engine.config.ConfigFile;
import io.github.enadeag.carchargeplanner.engine.config.ConfigProblem;
import io.github.enadeag.carchargeplanner.engine.config.EngineConfig;
import io.github.enadeag.carchargeplanner.engine.forecast.ForecastProvider;
import io.github.enadeag.carchargeplanner.engine.forecast.ForecastStep;
import io.github.enadeag.carchargeplanner.engine.forecast.ForecastUnavailable;
import io.github.enadeag.carchargeplanner.engine.plan.Planner;
import java.time.Clock;
import java.util.List;

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
            forecast = forecasts.forecast(config.system());
        } catch (ForecastUnavailable e) {
            return new Outlook.NoForecast(e.getMessage());
        }
        var now = clock.instant();
        var days = new Planner(config.system(), config.planning()).plan(forecast, ASSUMED_BATTERY_PCT, now);
        return new Outlook.Planned(now, config.system().timezone(), days, ASSUMED_BATTERY_PCT, true);
    }
}
