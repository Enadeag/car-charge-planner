package nl.enadeag.carchargeplanner.engine.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class HomeChargePowerTest {

    @ParameterizedTest
    @CsvSource({"KW_3_7, ONE, 16, 3680", "KW_7_4, ONE, 32, 7360", "KW_11, THREE, 16, 11040", "KW_22, THREE, 32, 22080"})
    void eachChoiceSetsItsPhasesAndCurrentAt230Volts(HomeChargePower choice, Phases phases, int maxAmps, int watts) {
        assertThat(choice.phases()).isEqualTo(phases);
        assertThat(choice.maxAmps()).isEqualTo(maxAmps);
        assertThat(phases.count() * maxAmps * HomeChargePower.VOLTS).isEqualTo(watts);
    }
}
