package nl.enadeag.carchargeplanner.platform.windows;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

/** Enter on the tray icon after Win+B, told apart from a double click, which AWT reports the same way. */
class TrayActionSourceTest {

    private final TrayActionSource source = new TrayActionSource(Duration.ofMillis(500));

    @Test
    void anActionWithoutAPressOfTheMouseIsTheKeyboard() {
        assertThat(source.cameFromKeyboard(10_000)).isTrue();
    }

    @Test
    void anActionRightAfterAPressIsADoubleClick() {
        source.recordPress(10_000);

        assertThat(source.cameFromKeyboard(10_200)).isFalse();
    }

    @Test
    void anActionLongAfterTheLastPressIsTheKeyboardAgain() {
        source.recordPress(10_000);

        assertThat(source.cameFromKeyboard(12_000)).isTrue();
    }
}
