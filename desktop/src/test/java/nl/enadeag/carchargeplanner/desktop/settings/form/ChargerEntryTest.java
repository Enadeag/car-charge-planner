package nl.enadeag.carchargeplanner.desktop.settings.form;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import nl.enadeag.carchargeplanner.engine.config.Charger;
import nl.enadeag.carchargeplanner.engine.config.Phases;
import nl.enadeag.carchargeplanner.engine.setting.ConfigSetting;
import nl.enadeag.carchargeplanner.engine.setting.InvalidSetting;
import org.junit.jupiter.api.Test;

class ChargerEntryTest {

    @Test
    void thePageShowsTheEfficiencyAsAPercentage() {
        assertThat(ChargerEntry.of(new Charger(Phases.THREE, 6, 32, 230, 0.92)))
                .isEqualTo(new ChargerEntry(Phases.THREE, 6, 32, 230, 92));
    }

    @Test
    void whatThePageShowsMakesTheSameChargerAgain() {
        assertThat(ChargerEntry.of(Charger.TYPICAL).charger()).isEqualTo(Charger.TYPICAL);
    }

    @Test
    void theCurrentsAreRoundedToWholeAmps() {
        assertThat(new ChargerEntry(Phases.ONE, 6.4, 15.5, 230, 90).charger())
                .isEqualTo(new Charger(Phases.ONE, 6, 16, 230, 0.9));
    }

    @Test
    void aValueTheChargerRefusesNamesItsSetting() {
        assertThatThrownBy(() -> new ChargerEntry(Phases.ONE, 16, 10, 230, 90).charger())
                .isInstanceOfSatisfying(InvalidSetting.class, refused -> {
                    assertThat(refused.setting()).isEqualTo(ConfigSetting.CHARGER_MAX_CURRENT);
                    assertThat(refused.rule()).isEqualTo(InvalidSetting.Rule.BELOW_MINIMUM_CURRENT);
                });
        assertThatThrownBy(() -> new ChargerEntry(Phases.ONE, 6, 16, 230, 101).charger())
                .isInstanceOfSatisfying(InvalidSetting.class,
                        refused -> assertThat(refused.setting()).isEqualTo(ConfigSetting.CHARGER_EFFICIENCY));
    }
}
