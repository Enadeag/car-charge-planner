package nl.enadeag.carchargeplanner.host;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** How the host tells a Flatpak sandbox, and the runtime folder the app uses there. */
class HostTest {

    @TempDir
    Path temp;

    @Test
    void aFlatpakIsToldByItsInfoFileOrItsId() throws Exception {
        var info = temp.resolve(".flatpak-info");

        assertThat(Host.inFlatpak(Map.of(), info)).isFalse();
        assertThat(Host.inFlatpak(Map.of("FLATPAK_ID", AppName.FLATPAK_ID), info)).isTrue();
        Files.writeString(info, "[Application]\nname=" + AppName.FLATPAK_ID + "\n");
        assertThat(Host.inFlatpak(Map.of(), info)).isTrue();
    }

    @Test
    void inAFlatpakTheRuntimeFolderIsTheOneEveryRunOfTheAppShares() {
        var shared = Host.sharedRuntimeFolder(Map.of("XDG_RUNTIME_DIR", "/run/user/1000", "HOME", "/home/me"));

        assertThat(shared).containsEntry("XDG_RUNTIME_DIR", "/run/user/1000/app/" + AppName.FLATPAK_ID)
                .containsEntry("HOME", "/home/me");
    }

    @Test
    void withoutAUsableRuntimeFolderTheEnvironmentStaysAsItIs() {
        assertThat(Host.sharedRuntimeFolder(Map.of())).isEmpty();
        assertThat(Host.sharedRuntimeFolder(Map.of("XDG_RUNTIME_DIR", "relative")))
                .containsExactlyEntriesOf(Map.of("XDG_RUNTIME_DIR", "relative"));
        assertThat(Host.sharedRuntimeFolder(Map.of("XDG_RUNTIME_DIR", ""))).containsEntry("XDG_RUNTIME_DIR", "");
    }
}
