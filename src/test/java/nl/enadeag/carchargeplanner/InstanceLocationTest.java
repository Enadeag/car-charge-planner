package nl.enadeag.carchargeplanner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

class InstanceLocationTest {

    @TempDir
    Path temp;

    private Path home() {
        return temp.resolve("home");
    }

    @Test
    void onLinuxTheFolderIsInTheUsersRuntimeFolder() throws Exception {
        var runtime = Files.createDirectory(temp.resolve("runtime"));
        assertThat(InstanceLocation.folder(Os.LINUX, Map.of("XDG_RUNTIME_DIR", runtime.toString()), home()))
                .isEqualTo(runtime.resolve("car-charge-planner"));
    }

    @Test
    void onLinuxWithoutAUsableRuntimeFolderTheFolderIsInTheAppsDataFolderNotInTheTemporaryFolder() {
        var fallback = home().resolve(".local/share/car-charge-planner/run");
        assertThat(InstanceLocation.folder(Os.LINUX, Map.of(), home())).isEqualTo(fallback);
        assertThat(InstanceLocation.folder(Os.LINUX, Map.of("XDG_RUNTIME_DIR", ""), home())).isEqualTo(fallback);
        assertThat(InstanceLocation.folder(Os.LINUX, Map.of("XDG_RUNTIME_DIR", "relative"), home()))
                .isEqualTo(fallback);
        assertThat(InstanceLocation.folder(Os.LINUX, Map.of("XDG_RUNTIME_DIR", temp.resolve("gone").toString()),
                home())).isEqualTo(fallback);
    }

    @Test
    void onLinuxWithoutAUsableRuntimeFolderTheFolderFollowsTheDataHome() {
        var dataHome = temp.resolve("data-home");
        assertThat(InstanceLocation.folder(Os.LINUX, Map.of("XDG_DATA_HOME", dataHome.toString()), home()))
                .isEqualTo(dataHome.resolve("car-charge-planner/run"));
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void onLinuxARuntimeFolderTheUserMayNotWriteInIsNotUsed() throws Exception {
        var runtime = Files.createDirectory(temp.resolve("runtime"),
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("r-x------")));
        assumeTrue(!Files.isWritable(runtime), "root may write anywhere");
        assertThat(InstanceLocation.folder(Os.LINUX, Map.of("XDG_RUNTIME_DIR", runtime.toString()), home()))
                .isEqualTo(home().resolve(".local/share/car-charge-planner/run"));
    }

    @Test
    void onWindowsTheFolderIsTheAppsInTheLocalAppData() {
        var local = temp.resolve("AppData/Local");
        assertThat(InstanceLocation.folder(Os.WINDOWS, Map.of("LOCALAPPDATA", local.toString()), home()))
                .isEqualTo(local.resolve("car-charge-planner"));
    }
}
