package nl.enadeag.carchargeplanner.host;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import nl.enadeag.carchargeplanner.instance.Owner;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/** The private folders for the icons the tray and the notifications hand to other programs. */
@EnabledOnOs(OS.LINUX)
class RuntimeFolderTest {

    @TempDir
    Path runtime;

    @TempDir
    Path temporary;

    private UserPrincipal user;

    @BeforeEach
    void whoRunsTheTests() throws IOException {
        user = Files.getOwner(Path.of("/proc/self"));
    }

    private static String mode(Path folder) throws IOException {
        return PosixFilePermissions.toString(Files.getPosixFilePermissions(folder, LinkOption.NOFOLLOW_LINKS));
    }

    @Test
    void inTheRuntimeFolderItIsNamedAfterThePurposeAndProcessAndOnlyTheUserMayUseIt() throws IOException {
        var folder = RuntimeFolder.make("tray", Map.of("XDG_RUNTIME_DIR", runtime.toString()), temporary, new Owner.User(user));

        assertThat(folder).isEqualTo(runtime.resolve("car-charge-planner-tray-" + ProcessHandle.current().pid()));
        assertThat(mode(folder)).isEqualTo("rwx------");
        assertThat(Files.getOwner(folder, LinkOption.NOFOLLOW_LINKS)).isEqualTo(user);
    }

    @Test
    void aLinkPlantedWhereTheFolderGoesIsRefused() throws IOException {
        var elsewhere = Files.createDirectory(temporary.resolve("elsewhere"));
        Files.createSymbolicLink(runtime.resolve("car-charge-planner-notify-" + ProcessHandle.current().pid()), elsewhere);

        assertThatThrownBy(() -> RuntimeFolder.make("notify", Map.of("XDG_RUNTIME_DIR", runtime.toString()), temporary, new Owner.User(user)))
                .isInstanceOf(IOException.class).hasMessageContaining("link");
    }

    @Test
    void aFolderThereAlreadyThatOthersMayUseIsRefused() throws IOException {
        var open = runtime.resolve("car-charge-planner-tray-" + ProcessHandle.current().pid());
        Files.createDirectory(open, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwxrwxrwx")));
        Files.setPosixFilePermissions(open, PosixFilePermissions.fromString("rwxrwxrwx"));

        assertThatThrownBy(() -> RuntimeFolder.make("tray", Map.of("XDG_RUNTIME_DIR", runtime.toString()), temporary, new Owner.User(user)))
                .isInstanceOf(IOException.class).hasMessageContaining("0700");
    }

    @Test
    void withoutARuntimeFolderItGetsANewRandomNameInTheTemporaryFolder() throws IOException {
        var first = RuntimeFolder.make("tray", Map.of(), temporary, new Owner.User(user));
        var second = RuntimeFolder.make("tray", Map.of("XDG_RUNTIME_DIR", "relative/path"), temporary, new Owner.User(user));

        assertThat(first.getParent()).isEqualTo(temporary);
        assertThat(first.getFileName().toString()).startsWith("car-charge-planner-tray-");
        assertThat(second).isNotEqualTo(first);
        assertThat(mode(first)).isEqualTo("rwx------");
        assertThat(mode(second)).isEqualTo("rwx------");
    }
}
