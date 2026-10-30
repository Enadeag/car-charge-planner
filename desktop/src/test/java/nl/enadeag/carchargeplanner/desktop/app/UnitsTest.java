package nl.enadeag.carchargeplanner.desktop.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class UnitsTest {

    @Test
    void aDistanceIsShownAndTypedInKmOrInMiles() {
        assertThat(Units.METRIC.distance(120)).isEqualTo(120);
        assertThat(Units.METRIC.km(120)).isEqualTo(120);
        assertThat(Units.IMPERIAL.distance(160.9344)).isEqualTo(100, within(1e-9));
        assertThat(Units.IMPERIAL.km(100)).isEqualTo(160.9344, within(1e-9));
        assertThat(Units.METRIC.distanceUnit()).isEqualTo("km");
        assertThat(Units.IMPERIAL.distanceUnit()).isEqualTo("mi");
    }
}
