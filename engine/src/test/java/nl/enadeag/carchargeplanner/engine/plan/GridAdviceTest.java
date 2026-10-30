package nl.enadeag.carchargeplanner.engine.plan;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import nl.enadeag.carchargeplanner.engine.config.Percent;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GridAdviceTest {

    private static final LocalDate DAY = LocalDate.of(2026, 7, 3);
    private static final Window WINDOW = new Window(Instant.parse("2026-07-03T05:00:00Z"),
            Instant.parse("2026-07-03T06:00:00Z"), 6, 1.38, 0, 1.38);

    @Test
    void aWindowWithoutAChargeLimitIsRefused() {
        assertThatThrownBy(() -> new GridAdvice(DAY, 20, 10, 7.8, Optional.of(WINDOW), Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aChargeLimitWithoutAWindowIsRefused() {
        assertThatThrownBy(() -> new GridAdvice(DAY, 20, 10, 7.8, Optional.empty(), Optional.of(new Percent(40))))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
