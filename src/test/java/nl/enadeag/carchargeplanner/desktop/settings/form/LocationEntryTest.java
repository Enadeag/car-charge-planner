package nl.enadeag.carchargeplanner.desktop.settings.form;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import nl.enadeag.carchargeplanner.engine.config.Coordinates;
import nl.enadeag.carchargeplanner.engine.config.EngineConfig;
import nl.enadeag.carchargeplanner.engine.config.ProviderId;
import nl.enadeag.carchargeplanner.engine.setting.ConfigSetting;
import nl.enadeag.carchargeplanner.engine.setting.InvalidSetting;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class LocationEntryTest {

    private static final ZoneId AMSTERDAM = ZoneId.of("Europe/Amsterdam");
    private static final ZoneId LONDON = ZoneId.of("Europe/London");

    @Test
    void aPlaceIsChosenOnceTheCoordinatesAreValidAndNotTheUnchosenOnes() {
        assertThat(new LocationEntry(52.37, 4.9, AMSTERDAM).isChosen()).isTrue();
        assertThat(new LocationEntry(Coordinates.NOT_CHOSEN.latitude(), Coordinates.NOT_CHOSEN.longitude(), AMSTERDAM)
                .isChosen()).isFalse();
        assertThat(new LocationEntry(91, 4.9, AMSTERDAM).isChosen()).isFalse();
    }

    @Test
    void theConfigTakesThePlaceAndTheTimeZoneAndKeepsTheRest() {
        var config = EngineConfig.setUpDraft(AMSTERDAM, new ProviderId("open-meteo"));

        var entered = new LocationEntry(51.5, -0.12, LONDON).applyTo(config);

        assertThat(entered.system().location()).isEqualTo(new Coordinates(51.5, -0.12));
        assertThat(entered.system().timezone()).isEqualTo(LONDON);
        assertThat(entered.system().panels()).isEqualTo(config.system().panels());
        assertThat(entered.typical()).isEqualTo(config.typical());
        assertThat(entered.planning()).isEqualTo(config.planning());
    }

    @Test
    void aCoordinateOutOfRangeIsRefusedAtItsField() {
        var config = EngineConfig.setUpDraft(AMSTERDAM, new ProviderId("open-meteo"));

        assertThatThrownBy(() -> new LocationEntry(52, 181, AMSTERDAM).applyTo(config))
                .isInstanceOfSatisfying(InvalidSetting.class,
                        refused -> assertThat(refused.setting()).isEqualTo(ConfigSetting.LONGITUDE));
    }
}
