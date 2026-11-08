package nl.enadeag.carchargeplanner.engine.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import nl.enadeag.carchargeplanner.engine.config.Percent;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class WindowTest {

    private static final Instant TEN = Instant.parse("2026-07-03T10:00:00Z");
    private static final Instant TWELVE = Instant.parse("2026-07-03T12:00:00Z");

    private static Window window(double solarKwh, double importKwh) {
        return new Window(TEN, TWELVE, 16, 3.68, solarKwh, importKwh);
    }

    @Test
    void theEnergyIsTheSolarAndTheGridEnergyTogether() {
        assertThat(window(3, 1.5).energyKwh()).isEqualTo(4.5);
        assertThat(window(0, 0).energyKwh()).isEqualTo(0.0);
    }

    @Test
    void theSolarShareIsTheSolarPartOfAllTheEnergyInPercent() {
        assertThat(window(3, 1).solarSharePct()).isEqualTo(new Percent(75));
        assertThat(window(2, 0).solarSharePct()).isEqualTo(new Percent(100));
        assertThat(window(0, 2).solarSharePct()).isEqualTo(new Percent(0));
    }

    @Test
    void aWindowWithoutEnergyHasNoSolarShare() {
        assertThat(window(0, 0).solarSharePct()).isEqualTo(new Percent(0));
    }

    @Test
    void aWindowRunsFromItsStartUpToButNotIncludingItsEnd() {
        var window = window(3, 1);
        assertThat(window.isRunningAt(TEN.minusNanos(1))).isFalse();
        assertThat(window.isRunningAt(TEN)).isTrue();
        assertThat(window.isRunningAt(TWELVE.minusNanos(1))).isTrue();
        assertThat(window.isRunningAt(TWELVE)).isFalse();
    }

    @Test
    void theRemainderKeepsStartEndAndPowerAndTheShareOfTheEnergyStillToCome() {
        var remainder = window(4, 1).remainingAfter(Instant.parse("2026-07-03T10:30:00Z"));
        assertThat(remainder.start()).isEqualTo(TEN);
        assertThat(remainder.end()).isEqualTo(TWELVE);
        assertThat(remainder.amps()).isEqualTo(16);
        assertThat(remainder.powerKw()).isEqualTo(3.68);
        assertThat(remainder.solarKwh()).isCloseTo(3, within(1e-12));
        assertThat(remainder.importKwh()).isCloseTo(0.75, within(1e-12));
    }

    @Test
    void atItsStartTheWholeWindowRemains() {
        assertThat(window(4, 1).remainingAfter(TEN)).isEqualTo(window(4, 1));
    }
}
