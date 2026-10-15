package io.github.enadeag.carchargeplanner;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

class InstanceLocationTest {

    private static final Path TEMP = Path.of("/tmp").toAbsolutePath();

    @Test
    void onLinuxTheFolderIsInTheUsersRuntimeFolder() {
        var runtime = Path.of("/run/user/1000").toAbsolutePath();
        assertThat(InstanceLocation.folder("Linux", Map.of("XDG_RUNTIME_DIR", runtime.toString()), TEMP, "someone"))
                .isEqualTo(runtime.resolve("car-charge-planner"));
    }

    @Test
    void onLinuxWithoutAUsableRuntimeFolderTheFolderIsTheUsersOwnInTheTemporaryFolder() {
        assertThat(InstanceLocation.folder("Linux", Map.of(), TEMP, "someone"))
                .isEqualTo(TEMP.resolve("car-charge-planner-someone"));
        assertThat(InstanceLocation.folder("Linux", Map.of("XDG_RUNTIME_DIR", "relative"), TEMP, "someone"))
                .isEqualTo(TEMP.resolve("car-charge-planner-someone"));
    }

    @Test
    void onWindowsTheFolderIsTheAppsInTheLocalAppData() {
        var local = Path.of("/Users/someone/AppData/Local").toAbsolutePath();
        assertThat(InstanceLocation.folder("Windows 11", Map.of("LOCALAPPDATA", local.toString()), TEMP, "someone"))
                .isEqualTo(local.resolve("car-charge-planner"));
    }
}
