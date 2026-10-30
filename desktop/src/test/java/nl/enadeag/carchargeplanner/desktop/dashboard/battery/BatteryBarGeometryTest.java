package nl.enadeag.carchargeplanner.desktop.dashboard.battery;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Where the battery bar puts percentages, without a screen. */
class BatteryBarGeometryTest {

    @Test
    void aBatteryLevelIsItsShareOfTheBar() {
        assertThat(BatteryBar.x(50, 300)).isEqualTo(150);
        assertThat(BatteryBar.x(0, 300)).isZero();
        assertThat(BatteryBar.x(120, 300)).isEqualTo(300);
    }

    @Test
    void aMarksLabelIsCentredBelowItButStaysOnTheBar() {
        assertThat(BatteryBar.labelX(150, 40, 300)).isEqualTo(130);
        assertThat(BatteryBar.labelX(5, 40, 300)).isZero();
        assertThat(BatteryBar.labelX(295, 40, 300)).isEqualTo(260);
        assertThat(BatteryBar.labelX(10, 400, 300)).isZero();
    }
}
