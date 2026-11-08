package nl.enadeag.carchargeplanner.engine.plan;

import static nl.enadeag.carchargeplanner.engine.plan.Forecasts.PREFERENCES;
import static nl.enadeag.carchargeplanner.engine.plan.Forecasts.at;
import static nl.enadeag.carchargeplanner.engine.plan.Forecasts.hourly;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import nl.enadeag.carchargeplanner.engine.config.Percent;
import nl.enadeag.carchargeplanner.engine.config.PlanningPreferences;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The choice of windows in the runs of one day, at the edges: the shortest window and a tie between two runs. */
class WindowFinderTest {

    private static final LocalDate DAY = LocalDate.of(2026, 7, 3);

    private final WindowFinder finder = new WindowFinder(Forecasts.golden(), PREFERENCES);

    @Test
    void aRunOfExactlyTheMinimumWindowGivesAWindow() {
        var preferences = new PlanningPreferences(new Percent(30), new Percent(80), 0.85, 0.2, Duration.ofHours(1),
                Duration.ofMinutes(15));
        var finder = new WindowFinder(Forecasts.golden(), preferences);
        var runs = finder.runs(hourly(DAY, Map.of(11, 6.0)), at(DAY, 0), List.of());

        var windows = finder.windows(runs, 10);

        assertThat(windows).extracting(Window::start, Window::end).containsExactly(
                tuple(at(DAY, 11), at(DAY, 12)));
    }

    @Test
    void aRunShorterThanTheMinimumWindowGivesNone() {
        var runs = finder.runs(hourly(DAY, Map.of(11, 6.0)), at(DAY, 0), List.of());
        var preferences = new PlanningPreferences(new Percent(30), new Percent(80), 0.85, 0.2,
                Duration.ofMinutes(61), Duration.ofMinutes(15));

        assertThat(new WindowFinder(Forecasts.golden(), preferences).windows(runs, 10)).isEmpty();
    }

    @Test
    void ofTwoRunsThatGiveTheSameEnergyTheEarlierComesFirstWhicheverIsListedFirst() {
        var forecast = new ArrayList<>(hourly(DAY, Map.of(10, 6.0, 15, 6.0)));
        var runs = finder.runs(forecast, at(DAY, 0), List.of());
        assertThat(runs).hasSize(2);
        var earlier = runs.get(0);
        var later = runs.get(1);
        // less than one window holds, so only one of the two is chosen
        double needed = 2;

        assertThat(finder.windows(List.of(earlier, later), needed)).extracting(Window::start)
                .containsExactly(at(DAY, 10));
        assertThat(finder.windows(List.of(later, earlier), needed)).extracting(Window::start)
                .containsExactly(at(DAY, 10));
    }
}
