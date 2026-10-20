package nl.enadeag.carchargeplanner.platform.linux;

import static org.assertj.core.api.Assertions.assertThat;

import nl.enadeag.carchargeplanner.desktop.system.AppIcon;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/** Everything goes to a temporary data folder: never to the real menu. */
@EnabledOnOs(OS.LINUX)
class ApplicationMenuEntryTest {

    private static final String PROGRAM = "/opt/car-charge-planner/bin/car-charge-planner";

    @TempDir
    Path data;

    private final List<Path> refreshed = new ArrayList<>();

    private ApplicationMenuEntry menuEntry(String program) {
        return new ApplicationMenuEntry(data, Path.of(program), "car-charge-planner", "Car charge planner",
                refreshed::add);
    }

    private Path entry() {
        return data.resolve("applications/car-charge-planner.desktop");
    }

    private List<Path> everythingUnderTheDataFolder() throws IOException {
        try (Stream<Path> all = Files.walk(data)) {
            return all.filter(Files::isRegularFile).sorted().toList();
        }
    }

    @Test
    void theEntryStartsTheQuotedLauncherWithTheAppIconInTheMenu() throws Exception {
        menuEntry(PROGRAM).install();
        assertThat(entry()).hasContent("""
                [Desktop Entry]
                Type=Application
                Name=Car charge planner
                Exec="/opt/car-charge-planner/bin/car-charge-planner"
                Icon=car-charge-planner
                Terminal=false
                Categories=Utility;
                """);
    }

    @Test
    void theEntryPassesDesktopFileValidate() throws Exception {
        menuEntry("/home/me/My apps/100%/car-charge-planner").install();
        var process = SystemPrograms.start(
                new ProcessBuilder("desktop-file-validate", entry().toString()).redirectErrorStream(true));
        var output = new String(process.getInputStream().readAllBytes());
        assertThat(process.waitFor(30, TimeUnit.SECONDS)).isTrue();
        assertThat(output).isEmpty();
        assertThat(process.exitValue()).isZero();
    }

    @Test
    void theIconIsInEverySizeAndAsScalableDrawing() throws Exception {
        var written = menuEntry(PROGRAM).install();
        var hicolor = data.resolve("icons/hicolor");
        for (var size : AppIcon.SIZES) {
            var png = hicolor.resolve(size + "x" + size + "/apps/car-charge-planner.png");
            assertThat(written).contains(png);
            assertThat(new AppIcon().image(size).getWidth()).isEqualTo(size);
            assertThat(Files.readAllBytes(png)).isEqualTo(
                    AppIcon.class.getResourceAsStream("icons/icon-" + size + ".png").readAllBytes());
        }
        assertThat(Files.readString(hicolor.resolve("scalable/apps/car-charge-planner.svg"))).contains("<svg");
        assertThat(written).hasSize(2 + AppIcon.SIZES.size()).startsWith(entry());
    }

    @Test
    void installingAgainFromAMovedFolderUpdatesTheEntryAndKeepsTheIcons() throws Exception {
        menuEntry(PROGRAM).install();
        var before = everythingUnderTheDataFolder();
        menuEntry("/home/me/moved/car-charge-planner/bin/car-charge-planner").install();
        assertThat(Files.readString(entry())).contains("Exec=\"/home/me/moved/car-charge-planner/bin/car-charge-planner\"\n")
                .doesNotContain("/opt/");
        assertThat(everythingUnderTheDataFolder()).isEqualTo(before);
    }

    @Test
    void uninstallingRemovesWhatWasWrittenAndNothingElse() throws Exception {
        var other = data.resolve("applications/someone-else.desktop");
        var otherIcon = data.resolve("icons/hicolor/48x48/apps/someone-else.png");
        Files.createDirectories(other.getParent());
        Files.createDirectories(otherIcon.getParent());
        Files.writeString(other, "[Desktop Entry]\n");
        Files.writeString(otherIcon, "png");
        var written = menuEntry(PROGRAM).install();

        assertThat(menuEntry(PROGRAM).uninstall()).containsExactlyInAnyOrderElementsOf(written);
        assertThat(everythingUnderTheDataFolder()).containsExactlyInAnyOrder(other, otherIcon);
    }

    @Test
    void uninstallingWhatIsNotThereRemovesNothing() throws Exception {
        assertThat(menuEntry(PROGRAM).uninstall()).isEmpty();
        assertThat(refreshed).isEmpty();
    }

    @Test
    void theCachesAreToldAfterEveryChangeWithTheDataFolder() throws Exception {
        menuEntry(PROGRAM).install();
        menuEntry(PROGRAM).uninstall();
        assertThat(refreshed).containsExactly(data, data);
    }

    @Test
    void refreshingTheCachesWorksWithOrWithoutTheTools() throws Exception {
        menuEntry(PROGRAM).install();
        ApplicationMenuEntry.refreshCaches(data);
        assertThat(entry()).exists();
    }

    @Test
    void aProgramFromTheDebOrRpmHasThePackagesMenuEntry() throws Exception {
        // jpackage's layout in /opt/car-charge-planner: the launcher in bin, a copy of the package's entry in lib
        var app = data.resolve("opt/car-charge-planner");
        Files.createDirectories(app.resolve("lib"));
        Files.writeString(app.resolve("lib/car-charge-planner-car-charge-planner.desktop"), "[Desktop Entry]\n");

        assertThat(ApplicationMenuEntry.packageEntry(app.resolve("bin/car-charge-planner"), "car-charge-planner"))
                .contains("car-charge-planner-car-charge-planner");
    }

    @Test
    void aProgramFromTheTarGzHasNoPackageMenuEntry() throws Exception {
        // the unpacked app image has the icon in lib, but no menu entry
        var app = data.resolve("car-charge-planner");
        Files.createDirectories(app.resolve("lib"));
        Files.writeString(app.resolve("lib/car-charge-planner.png"), "png");

        assertThat(ApplicationMenuEntry.packageEntry(app.resolve("bin/car-charge-planner"), "car-charge-planner"))
                .isEmpty();
        assertThat(ApplicationMenuEntry.packageEntry(Path.of("/car-charge-planner"), "car-charge-planner")).isEmpty();
    }
}
