package io.github.enadeag.carchargeplanner.platform.windows;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.enadeag.carchargeplanner.desktop.system.PanelShade;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

@EnabledOnOs(OS.WINDOWS)
class TaskbarShadeTest {

    @Test
    void theShadeIsWhatWindowsOwnRegistryToolReports() throws IOException, InterruptedException {
        var reg = new ProcessBuilder("reg", "query", "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
                "/v", "SystemUsesLightTheme").redirectErrorStream(true).start();
        var output = new String(reg.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        reg.waitFor();
        // "SystemUsesLightTheme    REG_DWORD    0x1"; no such line when the setting is missing, which means dark
        var expected = output.contains("REG_DWORD") && output.strip().endsWith("0x1") ? PanelShade.LIGHT : PanelShade.DARK;

        assertThat(new TaskbarShade().read()).isEqualTo(expected);
    }
}
