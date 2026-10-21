package nl.enadeag.carchargeplanner.engine.notify;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import nl.enadeag.carchargeplanner.engine.config.Percent;
import nl.enadeag.carchargeplanner.engine.config.Phases;
import nl.enadeag.carchargeplanner.engine.plan.Window;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AnnouncementTest {

    private static final ZoneId BRUSSELS = ZoneId.of("Europe/Brussels");
    private static final Window WINDOW = new Window(Instant.parse("2026-07-03T05:00:00Z"),
            Instant.parse("2026-07-03T06:00:00Z"), 6, 1.38, 0, 1.38);

    @Test
    void aGridChargeWithoutAChargeLimitIsRefused() {
        assertThatThrownBy(() -> new Announcement(AnnouncementKind.GRID_CHARGE, WINDOW, BRUSSELS, Phases.ONE,
                new Percent(30), Optional.empty())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aSolarWindowWithAChargeLimitIsRefused() {
        assertThatThrownBy(() -> new Announcement(AnnouncementKind.SOLAR_WINDOW, WINDOW, BRUSSELS, Phases.ONE,
                new Percent(30), Optional.of(new Percent(80)))).isInstanceOf(IllegalArgumentException.class);
    }
}
