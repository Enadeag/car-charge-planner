package nl.enadeag.carchargeplanner.platforms;

import static org.assertj.core.api.Assertions.assertThat;

import nl.enadeag.carchargeplanner.host.Host;
import nl.enadeag.carchargeplanner.host.Os;
import nl.enadeag.carchargeplanner.instance.Owner;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Which menu entry the notifications are tied to; nothing here connects to a bus. */
class FreedesktopPartsTest {

    @TempDir
    Path temp;

    private FreedesktopParts parts(Optional<Path> launcher) {
        return new FreedesktopParts(new Host(Os.LINUX, Map.of(), temp, temp, new Owner.Unchecked(), launcher, false));
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
}
