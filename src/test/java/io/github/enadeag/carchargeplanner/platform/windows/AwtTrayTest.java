package io.github.enadeag.carchargeplanner.platform.windows;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.enadeag.carchargeplanner.desktop.AppIcon;
import io.github.enadeag.carchargeplanner.desktop.Tray;
import org.junit.jupiter.api.Test;

/** The tests run without a desktop (java.awt.headless), as the app does when Windows starts it without one. */
class AwtTrayTest {

    @Test
    void withoutADesktopThereIsNoTrayInsteadOfACrash() {
        var tray = new AwtTray(new AppIcon(), "Car charge planner");
        assertThatThrownBy(() -> tray.show(new Tray.TrayActions(() -> { }, () -> { })))
                .isInstanceOf(Tray.TrayUnavailable.class);
    }
}
