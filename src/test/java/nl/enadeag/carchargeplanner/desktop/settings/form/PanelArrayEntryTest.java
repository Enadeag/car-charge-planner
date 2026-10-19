package nl.enadeag.carchargeplanner.desktop.settings.form;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import nl.enadeag.carchargeplanner.engine.config.PanelArray;
import nl.enadeag.carchargeplanner.engine.setting.InvalidSetting;
import nl.enadeag.carchargeplanner.engine.setting.Setting;
import org.junit.jupiter.api.Test;

class PanelArrayEntryTest {

    @Test
    void theCardShowsTheLossesAsAPercentage() {
        assertThat(PanelArrayEntry.of(new PanelArray("east roof", 4.2, 30, -90, 0.12)))
                .isEqualTo(new PanelArrayEntry("east roof", 4.2, 30, -90, 12));
    }

    @Test
    void whatTheCardShowsMakesTheSameArrayAgain() {
        assertThat(new PanelArrayEntry("garage", 2.5, 15, 45, 14).panelArray())
                .isEqualTo(new PanelArray("garage", 2.5, 15, 45, 0.14));
        assertThat(PanelArrayEntry.of(PanelArray.TYPICAL).panelArray()).isEqualTo(PanelArray.TYPICAL);
    }

    @Test
    void aValueTheArrayRefusesNamesItsSetting() {
        assertThatThrownBy(() -> new PanelArrayEntry("", 5, 35, 0, 100).panelArray())
                .isInstanceOfSatisfying(InvalidSetting.class,
                        refused -> assertThat(refused.setting()).isEqualTo(Setting.PANEL_LOSSES));
    }
}
