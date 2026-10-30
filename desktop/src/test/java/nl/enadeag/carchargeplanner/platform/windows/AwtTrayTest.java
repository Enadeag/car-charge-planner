package nl.enadeag.carchargeplanner.platform.windows;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import nl.enadeag.carchargeplanner.desktop.system.StatusIcon;
import nl.enadeag.carchargeplanner.desktop.system.Tray;
import javafx.application.ColorScheme;
import javafx.beans.property.SimpleObjectProperty;
import org.junit.jupiter.api.Test;

/** The tests run without a desktop (java.awt.headless), as the app does when Windows starts it without one. */
class AwtTrayTest {

    @Test
    void withoutADesktopThereIsNoTrayInsteadOfACrash() {
        var tray = new AwtTray(new StatusIcon(), new TaskbarShade(),
                new SimpleObjectProperty<>(ColorScheme.LIGHT), "Car charge planner");
        tray.setMenuLabels(new Tray.MenuLabels("Open dashboard", "Settings…", "About…", "Quit"));
        assertThatThrownBy(() -> tray.add(new Tray.Actions(where -> { }, () -> { }, () -> { }, () -> { }, () -> { })))
                .isInstanceOf(Tray.TrayUnavailable.class);
    }
}
