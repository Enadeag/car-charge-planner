package nl.enadeag.carchargeplanner.start;

import static org.assertj.core.api.Assertions.assertThat;

import nl.enadeag.carchargeplanner.desktop.system.Tray;
import nl.enadeag.carchargeplanner.desktop.system.TrayStatus;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TrayStarterTest {

    private static final Tray.Actions ACTIONS = new Tray.Actions(icon -> { }, () -> { }, () -> { }, () -> { },
            () -> { });

    private Instant now = Instant.parse("2026-10-17T07:00:00Z");
    private final InstantSource clock = () -> now;
    private final List<Duration> slept = new ArrayList<>();
    private final TrayStarter starter = new TrayStarter(clock, time -> {
        slept.add(time);
        now = now.plus(time);
    });

    /** A tray that is there from a given moment on. */
    private final class LateTray implements Tray {
        private final Instant comesAt;
        int shown;

        LateTray(Duration after) {
            this.comesAt = now.plus(after);
        }

        @Override
        public void show(Actions actions) throws TrayUnavailable {
            if (now.isBefore(comesAt)) {
                throw new TrayUnavailable("no tray yet", null);
            }
            shown++;
        }

        @Override
        public void showStatus(TrayStatus status) {
        }

        @Override
        public void showLabels(MenuLabels labels) {
        }

        @Override
        public void remove() {
        }
    }

    @Test
    void aTrayThatIsThereIsShownAtOnce() throws InterruptedException {
        var tray = new LateTray(Duration.ZERO);

        assertThat(starter.show(tray, ACTIONS, Duration.ZERO)).isTrue();
        assertThat(tray.shown).isEqualTo(1);
        assertThat(slept).isEmpty();
    }

    @Test
    void aTrayThatComesWithinTheWaitIsShownWhenItComes() throws InterruptedException {
        var tray = new LateTray(Duration.ofSeconds(2));

        assertThat(starter.show(tray, ACTIONS, StartupDecision.TRAY_WAIT)).isTrue();
        assertThat(tray.shown).isEqualTo(1);
        assertThat(slept).hasSize(4).containsOnly(TrayStarter.RETRY);
    }

    @Test
    void aTrayThatDoesNotComeWithinTheWaitIsGivenUp() throws InterruptedException {
        var tray = new LateTray(Duration.ofSeconds(6));

        assertThat(starter.show(tray, ACTIONS, StartupDecision.TRAY_WAIT)).isFalse();
        assertThat(tray.shown).isZero();
        assertThat(slept.stream().reduce(Duration.ZERO, Duration::plus)).isEqualTo(StartupDecision.TRAY_WAIT);
    }

    @Test
    void withoutAWaitTheTrayIsTriedOnce() throws InterruptedException {
        var tray = new LateTray(Duration.ofMillis(1));

        assertThat(starter.show(tray, ACTIONS, Duration.ZERO)).isFalse();
        assertThat(slept).isEmpty();
    }
}
