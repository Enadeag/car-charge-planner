package io.github.enadeag.carchargeplanner.engine.plan;

import static io.github.enadeag.carchargeplanner.engine.plan.Forecasts.PREFERENCES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.github.enadeag.carchargeplanner.engine.config.Charger;
import io.github.enadeag.carchargeplanner.engine.forecast.ForecastStep;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

class ChargingTest {

    private static Charger charger(int phases) {
        return Forecasts.exampleOnPhases(phases).charger();
    }

    @ParameterizedTest
    @CsvSource({"1, 6, 1.38", "3, 6, 4.14", "1, 16, 3.68", "3, 16, 11.04", "1, 32, 7.36", "3, 32, 22.08"})
    void powerIsPhasesTimesCurrentTimesVoltage(int phases, int amps, double expectedKw) {
        assertThat(Charging.powerKw(amps, charger(phases))).isCloseTo(expectedKw, within(1e-9));
    }

    static List<Arguments> surplusOnOneAndThreePhases() {
        var arguments = new ArrayList<Arguments>();
        for (int phases : new int[] {1, 3}) {
            for (double surplusKw : new double[] {0.5, 1.38, 2.0, 4.13, 4.14, 5.0, 8.0, 11.0, 30.0}) {
                arguments.add(Arguments.of(surplusKw, phases));
            }
        }
        return arguments;
    }

    @ParameterizedTest
    @MethodSource("surplusOnOneAndThreePhases")
    void ampsForIsTheLargestWholeCurrentThatFits(double surplusKw, int phases) {
        var charger = charger(phases);
        int amps = Charging.ampsFor(surplusKw, charger);
        assertThat(amps).isEqualTo(Math.min(charger.maxAmps(), (int) (surplusKw * 1000 / (230 * phases))));
        if (amps > 0 && amps < charger.maxAmps()) {
            assertThat(Charging.powerKw(amps, charger)).isLessThanOrEqualTo(surplusKw + 1e-9);
            assertThat(Charging.powerKw(amps + 1, charger)).isGreaterThan(surplusKw + 1e-9);
        }
    }

    @Test
    void theSameSurplusGivesAThirdOfTheCurrentOnThreePhases() {
        assertThat(Charging.ampsFor(6.9, charger(1))).isEqualTo(30);
        assertThat(Charging.ampsFor(6.9, charger(3))).isEqualTo(10);
    }

    @Test
    void theCurrentForASurplusNeverAsksForMoreThanTheSurplus() {
        var system = Forecasts.exampleOnPhases(3);
        var noon = Instant.parse("2026-07-03T10:00:00Z");
        var step = new ForecastStep(noon, noon.plusSeconds(3600), 9.0);
        double surplus = Charging.usableKw(step, system, PREFERENCES);
        int amps = Charging.ampsFor(surplus, system.charger());
        assertThat(Charging.powerKw(amps, system.charger())).isLessThanOrEqualTo(surplus + 1e-9);
    }
}
