package nl.enadeag.carchargeplanner.platforms;

import static org.assertj.core.api.Assertions.assertThat;

import nl.enadeag.carchargeplanner.host.AppName;
import nl.enadeag.carchargeplanner.host.Host;
import nl.enadeag.carchargeplanner.host.Os;
import nl.enadeag.carchargeplanner.instance.Owner;
import nl.enadeag.carchargeplanner.platform.linux.LinuxStartAtLogin;
import nl.enadeag.carchargeplanner.platform.linux.PortalStartAtLogin;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Which menu entry the notifications are tied to, and how start at login works; nothing here connects to a bus. */
class FreedesktopPartsTest {

    @TempDir
    Path temp;

    private FreedesktopParts parts(Optional<Path> launcher) {
        return new FreedesktopParts(new Host(Os.LINUX, Map.of(), temp, temp, new Owner.Unchecked(), launcher, false,
                false));
    }

    @Test
    void fromTheDebOrRpmTheNotificationsAreTiedToThePackagesMenuEntry() throws Exception {
        var app = temp.resolve("opt/car-charge-planner");
        Files.createDirectories(app.resolve("lib"));
        Files.writeString(app.resolve("lib/car-charge-planner-car-charge-planner.desktop"), "[Desktop Entry]\n");

        assertThat(parts(Optional.of(app.resolve("bin/car-charge-planner"))).desktopEntry())
                .isEqualTo("car-charge-planner-car-charge-planner");
    }

    @Test
    void otherwiseToTheEntryThatInstallLauncherWrites() {
        var unpacked = temp.resolve("car-charge-planner/bin/car-charge-planner");

        assertThat(parts(Optional.of(unpacked)).desktopEntry()).isEqualTo("car-charge-planner");
        assertThat(parts(Optional.empty()).desktopEntry()).isEqualTo("car-charge-planner");
    }

    @Test
    void inAFlatpakTheNotificationsAreTiedToItsMenuEntryAndThePortalStartsItAtLogin() {
        var host = new Host(Os.LINUX, Map.of("XDG_CONFIG_HOME", temp.toString()), temp, temp, new Owner.Unchecked(),
                Optional.of(Path.of("/app/bin/car-charge-planner")), false, true);
        var parts = new FreedesktopParts(host);

        assertThat(parts.desktopEntry()).isEqualTo(AppName.FLATPAK_ID);
        assertThat(parts.startAtLogin()).containsInstanceOf(PortalStartAtLogin.class);
    }

    @Test
    void outsideAFlatpakTheAutostartFolderStartsIt() {
        assertThat(parts(Optional.of(temp.resolve("car-charge-planner/bin/car-charge-planner"))).startAtLogin())
                .containsInstanceOf(LinuxStartAtLogin.class);
        assertThat(parts(Optional.empty()).startAtLogin()).isEmpty();
    }
}
