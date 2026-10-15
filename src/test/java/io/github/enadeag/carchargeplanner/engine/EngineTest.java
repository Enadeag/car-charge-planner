package io.github.enadeag.carchargeplanner.engine;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.enadeag.carchargeplanner.engine.config.ConfigFile;
import io.github.enadeag.carchargeplanner.engine.forecast.ForecastProvider;
import io.github.enadeag.carchargeplanner.engine.forecast.ForecastStep;
import io.github.enadeag.carchargeplanner.engine.forecast.ForecastUnavailable;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EngineTest {

    private static final ConfigFile EXAMPLE = new ConfigFile(Path.of("docs", "example-engine.json"));
    private static final ZoneId BRUSSELS = ZoneId.of("Europe/Brussels");
    private static final LocalDate DAY = LocalDate.of(2026, 7, 3);

    /** Two sunny days: 4 kW from 10:00 to 16:00 local time, nothing the rest of the day. */
    private static final ForecastProvider SUNNY = system -> {
        var steps = new ArrayList<ForecastStep>();
        for (var day : List.of(DAY, DAY.plusDays(1))) {
            for (int hour = 0; hour < 24; hour++) {
                var start = day.atTime(hour, 0).atZone(BRUSSELS).toInstant();
                steps.add(new ForecastStep(start, start.plus(Duration.ofHours(1)), hour >= 10 && hour < 16 ? 4.0 : 0));
            }
        }
        return steps;
    };

    private static Clock clockAt(int hour) {
        return Clock.fixed(DAY.atTime(hour, 0).atZone(BRUSSELS).toInstant(), ZoneOffset.UTC);
    }

    @Test
    void plansFromTheForecastForTheSystemInTheConfigFile() {
        var outlook = new Engine(EXAMPLE, SUNNY, clockAt(7)).plan();

        var planned = (Outlook.Planned) outlook;
        assertThat(planned.zone()).isEqualTo(BRUSSELS);
        assertThat(planned.days()).extracting(day -> day.date()).containsExactly(DAY, DAY.plusDays(1));
        assertThat(planned.batteryPct()).isEqualTo(Engine.ASSUMED_BATTERY_PCT);
        // 4 kW less the 0.5 kW house, times the 0.85 margin: 2.975 kW. 13 A (2.99 kW) on one phase takes a little
        // from the grid, within the 0.2 kWh the example allows
        var window = planned.nextWindow().orElseThrow();
        assertThat(window.start()).isEqualTo(DAY.atTime(10, 0).atZone(BRUSSELS).toInstant());
        assertThat(window.amps()).isEqualTo(13);
    }

    @Test
    void theNextWindowIsTomorrowsOnceTodaysHasEnded() {
        var planned = (Outlook.Planned) new Engine(EXAMPLE, SUNNY, clockAt(17)).plan();

        var window = planned.nextWindow().orElseThrow();
        assertThat(window.start()).isEqualTo(DAY.plusDays(1).atTime(10, 0).atZone(BRUSSELS).toInstant());
    }

    @Test
    void withoutAConfigFileThereIsNoPlanButTheReasonNamesTheFile(@TempDir Path folder) {
        var missing = folder.resolve("engine.json");
        var outlook = new Engine(new ConfigFile(missing), SUNNY, clockAt(7)).plan();

        assertThat(outlook).isInstanceOfSatisfying(Outlook.NotSetUp.class,
                notSetUp -> assertThat(notSetUp.reason()).contains(missing.toString()));
    }

    @Test
    void withoutAForecastThereIsNoPlanButTheProvidersReason() {
        ForecastProvider down = system -> {
            throw new ForecastUnavailable("Open-Meteo did not answer the request (status 503)");
        };
        var outlook = new Engine(EXAMPLE, down, clockAt(7)).plan();

        assertThat(outlook).isEqualTo(new Outlook.NoForecast("Open-Meteo did not answer the request (status 503)"));
    }

    @Test
    void aPlanWithoutWindowsHasNoNextWindow() {
        var planned = new Outlook.Planned(Instant.parse("2026-07-03T05:00:00Z"), BRUSSELS, List.of(), 50, true);
        assertThat(planned.nextWindow()).isEmpty();
    }
}
