package nl.enadeag.carchargeplanner.engine.plan;

import static nl.enadeag.carchargeplanner.engine.plan.Forecasts.NIGHT_CHARGING;
import static nl.enadeag.carchargeplanner.engine.plan.Forecasts.TZ;
import static nl.enadeag.carchargeplanner.engine.plan.Forecasts.at;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class GridPlacementsTest {

    private static final LocalDate DAY = LocalDate.of(2026, 7, 3);
    private static final double TINY_KWH = 1e-12;
    private static final GridPlacements PLACEMENTS = new GridPlacements(Forecasts.example(), NIGHT_CHARGING);

    /** The quarters of the day, all free except the one starting at {@code busy}. */
    private static List<GridPlacements.QuarterHour> day(Instant busy) {
        var quarters = new ArrayList<GridPlacements.QuarterHour>();
        var start = DAY.atStartOfDay(TZ).toInstant();
        for (int i = 0; i < 96; i++) {
            var end = start.plus(Duration.ofMinutes(15));
            quarters.add(new GridPlacements.QuarterHour(start, end, 0, !start.equals(busy)));
            start = end;
        }
        return quarters;
    }

    @Test
    void anEnergyThatRoundsToNoQuarterTakesOneQuarterInTheChargingHours() {
        var quarters = day(null);

        var windows = PLACEMENTS.placements(quarters, TINY_KWH, 16, Instant.EPOCH);

        assertThat(windows).isNotEmpty().allSatisfy(window -> {
            assertThat(quarters).anyMatch(quarter -> quarter.start().equals(window.start()));
            assertThat(NIGHT_CHARGING.chargingHours().allowStart(window.start(), TZ)).isTrue();
            assertThat(window.energyKwh()).isEqualTo(TINY_KWH, within(1e-15));
            assertThat(Duration.between(window.start(), window.end())).isLessThanOrEqualTo(Duration.ofMinutes(15));
        });
    }

    @Test
    void aTinyEnergyStillNeedsItsQuarterToBeFree() {
        var busy = at(DAY, 21);

        var windows = PLACEMENTS.placements(day(busy), TINY_KWH, 16, Instant.EPOCH);

        assertThat(windows).isNotEmpty().noneMatch(window -> window.start().equals(busy));
    }
}
