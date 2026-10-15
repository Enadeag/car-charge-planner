package io.github.enadeag.carchargeplanner.desktop.settings;

import static org.assertj.core.api.Assertions.assertThat;

import javafx.geometry.Dimension2D;
import javafx.geometry.Rectangle2D;
import org.junit.jupiter.api.Test;

class WindowSizeTest {

    /** 1920 × 1080 at 200 %: what is left of it beside a 48 px taskbar. */
    private static final Rectangle2D SMALL = new Rectangle2D(0, 0, 960, 516);

    @Test
    void aWindowFollowsTheSizeOfTheText() {
        assertThat(WindowSize.on(60, 46, 13, new Rectangle2D(0, 0, 1920, 1032))).isEqualTo(new Dimension2D(780, 598));
    }

    @Test
    void onAScreenTooSmallForItAWindowTakesTheScreenAndItsPageScrolls() {
        assertThat(WindowSize.on(60, 46, 13, SMALL)).isEqualTo(new Dimension2D(780, 516));
        assertThat(WindowSize.on(60, 46, 26, SMALL)).isEqualTo(new Dimension2D(960, 516));
    }
}
