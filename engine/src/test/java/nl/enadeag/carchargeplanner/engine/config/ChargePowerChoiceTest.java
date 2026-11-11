package nl.enadeag.carchargeplanner.engine.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class ChargePowerChoiceTest {

    @ParameterizedTest
    @EnumSource(HomeChargePower.class)
    void eachPowerIsTheChoiceItsPhasesAndCurrentAt230VoltsStandFor(HomeChargePower power) {
        var choice = ChargePowerChoice.matching(power.phases(), power.maxAmps(), 230);

        assertThat(choice).isEqualTo(ChargePowerChoice.of(power)).isNotEqualTo(ChargePowerChoice.OTHER);
        assertThat(choice.power()).contains(power);
    }

    @Test
    void otherSetsNoPower() {
        assertThat(ChargePowerChoice.OTHER.power()).isEmpty();
    }

    @Test
    void onePhaseOf16AmpsIs3Point7KilowattAt230VoltsAndOtherAt240() {
        assertThat(ChargePowerChoice.of(new Charger(Phases.ONE, 6, 16, 230, 0.9))).isEqualTo(ChargePowerChoice.KW_3_7);
        assertThat(ChargePowerChoice.of(new Charger(Phases.ONE, 6, 16, 240, 0.9))).isEqualTo(ChargePowerChoice.OTHER);
    }

    @Test
    void theLowestCurrentAndTheEfficiencyPlayNoPart() {
        assertThat(ChargePowerChoice.of(new Charger(Phases.THREE, 10, 16, 230, 0.75)))
                .isEqualTo(ChargePowerChoice.KW_11);
    }

    @ParameterizedTest
    @CsvSource({"THREE, 25, 230", "ONE, 25, 230", "THREE, 13, 230", "ONE, 20, 230", "THREE, 32, 400",
        "THREE, 16, 229.9", "ONE, 32, 230.4"})
    void aChargerOfOtherValuesIsOtherNeverTheNearest(Phases phases, int maxAmps, double volts) {
        assertThat(ChargePowerChoice.matching(phases, maxAmps, volts)).isEqualTo(ChargePowerChoice.OTHER);
    }
}
