package nl.enadeag.carchargeplanner.engine.forecast;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import nl.enadeag.carchargeplanner.engine.config.Coordinates;
import nl.enadeag.carchargeplanner.engine.config.PanelArray;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ArraySumTest {

    private static final ZoneId AMSTERDAM = ZoneId.of("Europe/Amsterdam");

    private static Instant hour(int hour) {
        return hour(hour, 0);
    }

    private static Instant hour(int hour, int minute) {
        return LocalDate.of(2026, 7, 3).atTime(hour, minute).atZone(AMSTERDAM).toInstant();
    }

    private static ForecastStep step(int hour, double powerKw, int hours) {
        return new ForecastStep(hour(hour), hour(hour).plus(Duration.ofHours(hours)), powerKw);
    }

    private static ForecastStep step(int hour, double powerKw) {
        return step(hour, powerKw, 1);
    }

    @Test
    void stepsThatLineUpAreAddedStepByStep() {
        var a = List.of(step(8, 1.0), step(9, 2.0));
        var b = List.of(step(8, 0.5), step(9, 0.25));
        assertThat(ArraySum.addUp(List.of(a, b))).containsExactly(step(8, 1.5), step(9, 2.25));
    }

    @Test
    void stepsThatDoNotLineUpAreCutAndNoEnergyIsLost() {
        var a = List.of(step(8, 1.0, 2));
        var b = List.of(new ForecastStep(hour(8), hour(8, 30), 2.0), new ForecastStep(hour(8, 30), hour(10), 0.5));
        var total = ArraySum.addUp(List.of(a, b));

        assertThat(total).containsExactly(new ForecastStep(hour(8), hour(8, 30), 3.0), new ForecastStep(hour(8, 30), hour(10), 1.5));
        assertThat(total.stream().mapToDouble(s -> s.powerKw() * s.hours()).sum()).isCloseTo(2.0 + 1.0 + 0.75, within(1e-9));
    }

    @Test
    void aGapInOneArrayAddsNothingAndAGapInAllStaysAGap() {
        var a = List.of(step(8, 1.0), step(9, 1.0), step(12, 1.0));
        var b = List.of(step(8, 2.0), step(12, 2.0));
        assertThat(ArraySum.addUp(List.of(a, b))).containsExactly(step(8, 3.0), step(9, 1.0), step(12, 3.0));
    }

    @Test
    void theTotalCoversOnlyTheTimeEveryArrayCovers() {
        var a = List.of(step(8, 1.0), step(9, 1.0), step(10, 1.0));
        var b = List.of(step(9, 2.0));
        assertThat(ArraySum.addUp(List.of(a, b))).containsExactly(step(9, 3.0));
        assertThat(ArraySum.addUp(List.of(a, List.of()))).isEmpty();
        assertThat(ArraySum.addUp(List.of())).isEmpty();
    }

    @Test
    void oneForecastIsReturnedAsItIs() {
        var a = List.of(step(8, 1.0));
        assertThat(ArraySum.addUp(List.of(a))).isSameAs(a);
    }

    @Test
    void eachArrayIsAskedOnItsOwnAndInOrder() throws ProviderError {
        var east = new PanelArray("East", 3.0, 30, -90, 0.14);
        var west = new PanelArray("West", 2.5, 30, 90, 0.1);
        var system = new SolarSite(new Coordinates(52.37, 4.9), AMSTERDAM, List.of(east, west));
        var asked = new ArrayList<PanelArray>();

        var total = ArraySum.sumOverArrays(system, array -> {
            asked.add(array);
            return List.of(step(8, array.kwp()));
        });

        assertThat(total).containsExactly(step(8, 5.5));
        assertThat(asked).containsExactly(east, west);
    }
}
