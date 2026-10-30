package nl.enadeag.carchargeplanner.engine.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import java.time.Duration;

class NotANumberTest {

    private static final double NAN = Double.NaN;

    @Test
    void aCarRefusesAValueThatIsNotANumber() {
        assertThatThrownBy(() -> new Car(NAN, 17, 30)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("usableBatteryKwh must be more than 0");
        assertThatThrownBy(() -> new Car(60, NAN, 30)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("consumptionKwhPer100km must be more than 0");
        assertThatThrownBy(() -> new Car(60, 17, NAN)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("kmPerDay must not be negative");
    }

    @Test
    void aChargerRefusesAValueThatIsNotANumber() {
        assertThatThrownBy(() -> new Charger(Phases.THREE, 6, 16, NAN, 0.9))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("volts must be more than 0");
        assertThatThrownBy(() -> new Charger(Phases.THREE, 6, 16, 230, NAN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("efficiency must be more than 0 and at most 1");
    }

    @Test
    void aPanelArrayRefusesAValueThatIsNotANumber() {
        assertThatThrownBy(() -> new PanelArray("roof", NAN, 30, 0, 0.14))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("kwp must be more than 0");
        assertThatThrownBy(() -> new PanelArray("roof", 4, NAN, 0, 0.14))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("tiltDeg must be between 0 and 90");
        assertThatThrownBy(() -> new PanelArray("roof", 4, 30, NAN, 0.14))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("azimuthDeg must be between -180 and 180");
        assertThatThrownBy(() -> new PanelArray("roof", 4, 30, 0, NAN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("systemLosses must be at least 0 and less than 1");
    }

    @Test
    void aHouseRefusesABaseloadThatIsNotANumber() {
        assertThatThrownBy(() -> new House(NAN)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("baseloadKw must not be negative");
    }

    @Test
    void planningPreferencesRefuseAValueThatIsNotANumber() {
        var low = new Percent(20);
        var high = new Percent(80);
        assertThatThrownBy(() -> new PlanningPreferences(low, high, NAN, 0.5,
                Duration.ofMinutes(15), Duration.ofMinutes(10)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("margin must be more than 0 and at most 1");
        assertThatThrownBy(() -> new PlanningPreferences(low, high, 0.8, NAN,
                Duration.ofMinutes(15), Duration.ofMinutes(10)))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("importToleranceKwh must not be negative");
    }
}
