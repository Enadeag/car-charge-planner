package io.github.enadeag.carchargeplanner;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DataLocationTest {

    private static final Path HOME = Path.of("/home/someone").toAbsolutePath();

    @Test
    void onLinuxTheDataIsInTheUsersDataFolder() {
        assertThat(DataLocation.defaultFolder(Os.LINUX, Map.of(), HOME))
                .isEqualTo(HOME.resolve(".local/share/car-charge-planner"));
    }

    @Test
    void onLinuxAnAbsoluteXdgDataHomeIsFollowedAndARelativeOneIgnored() {
        var elsewhere = HOME.resolve("data");
        assertThat(DataLocation.defaultFolder(Os.LINUX, Map.of("XDG_DATA_HOME", elsewhere.toString()), HOME))
                .isEqualTo(elsewhere.resolve("car-charge-planner"));
        assertThat(DataLocation.defaultFolder(Os.LINUX, Map.of("XDG_DATA_HOME", "relative"), HOME))
                .isEqualTo(HOME.resolve(".local/share/car-charge-planner"));
    }

    @Test
    void onWindowsTheDataStaysOnThisComputer() {
        var local = HOME.resolve("AppData/Local");
        assertThat(DataLocation.defaultFolder(Os.WINDOWS, Map.of("LOCALAPPDATA", local.toString()), HOME))
                .isEqualTo(local.resolve("car-charge-planner"));
    }
}
