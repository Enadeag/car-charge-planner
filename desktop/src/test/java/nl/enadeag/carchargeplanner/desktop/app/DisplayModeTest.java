package nl.enadeag.carchargeplanner.desktop.app;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DisplayModeTest {

    @Test
    void theCodesAreTheNamesTheAppSettingsFileWrites() {
        assertThat(DisplayMode.AUTOMATIC.code()).isEqualTo("automatic");
        assertThat(DisplayMode.TRAY.code()).isEqualTo("tray");
        assertThat(DisplayMode.WINDOW.code()).isEqualTo("window");
    }

    @Test
    void aCodeFindsItsModeInAnyCase() {
        assertThat(DisplayMode.ofCode("Tray")).contains(DisplayMode.TRAY);
        assertThat(DisplayMode.ofCode("")).isEmpty();
    }
}
