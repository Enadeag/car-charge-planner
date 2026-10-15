package io.github.enadeag.carchargeplanner;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConfigLocationTest {

    private static final Path HOME = Path.of("/home/someone").toAbsolutePath();

    @Test
    void onLinuxTheFileIsInTheUsersConfigFolder() {
        assertThat(ConfigLocation.defaultPath("Linux", Map.of(), HOME))
                .isEqualTo(HOME.resolve(".config/car-charge-planner/engine.json"));
    }

    @Test
    void onLinuxAnAbsoluteXdgConfigHomeIsFollowedAndARelativeOneIgnored() {
        var elsewhere = HOME.resolve("settings");
        assertThat(ConfigLocation.defaultPath("Linux", Map.of("XDG_CONFIG_HOME", elsewhere.toString()), HOME))
                .isEqualTo(elsewhere.resolve("car-charge-planner/engine.json"));
        assertThat(ConfigLocation.defaultPath("Linux", Map.of("XDG_CONFIG_HOME", "relative"), HOME))
                .isEqualTo(HOME.resolve(".config/car-charge-planner/engine.json"));
    }

    @Test
    void onWindowsTheFileIsInTheRoamingAppData() {
        var appData = HOME.resolve("AppData/Roaming");
        assertThat(ConfigLocation.defaultPath("Windows 11", Map.of("APPDATA", appData.toString()), HOME))
                .isEqualTo(appData.resolve("car-charge-planner/engine.json"));
    }
}
