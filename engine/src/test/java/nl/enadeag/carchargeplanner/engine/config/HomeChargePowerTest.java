package nl.enadeag.carchargeplanner.engine.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class HomeChargePowerTest {

    @ParameterizedTest
    @CsvSource({"KW_3_7, ONE, 16, 3680", "KW_7_4, ONE, 32, 7360", "KW_11, THREE, 16, 11040", "KW_22, THREE, 32, 22080"})
    void eachChoiceSetsItsPhasesAndCurrentAt230Volts(HomeChargePower choice, Phases phases, int maxAmps, int watts) {
        assertThat(choice.phases()).isEqualTo(phases);
        assertThat(choice.maxAmps()).isEqualTo(maxAmps);
        assertThat(phases.count() * maxAmps * HomeChargePower.VOLTS).isEqualTo(watts);
        assertThat(HomeChargePower.matching(phases, maxAmps, 230)).contains(choice);
    }

    @Test
    void onePhaseOf16AmpsIs3Point7KilowattAt230VoltsAndOtherAt240() {
        assertThat(HomeChargePower.of(new Charger(Phases.ONE, 6, 16, 230, 0.9))).contains(HomeChargePower.KW_3_7);
        assertThat(HomeChargePower.of(new Charger(Phases.ONE, 6, 16, 240, 0.9))).isEmpty();
    }

    @Test
    void theLowestCurrentAndTheEfficiencyPlayNoPart() {
        assertThat(HomeChargePower.of(new Charger(Phases.THREE, 10, 16, 230, 0.75))).contains(HomeChargePower.KW_11);
    }

    @ParameterizedTest
    @CsvSource({"THREE, 25, 230", "ONE, 25, 230", "THREE, 13, 230", "ONE, 20, 230", "THREE, 32, 400",
        "THREE, 16, 229.9", "ONE, 32, 230.4"})
    void aChargerOfOtherValuesIsOtherNeverTheNearest(Phases phases, int maxAmps, double volts) {
        assertThat(HomeChargePower.matching(phases, maxAmps, volts)).isEqualTo(Optional.empty());
    }
}
