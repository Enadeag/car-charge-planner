package io.github.enadeag.carchargeplanner.platform.linux;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/** Linux paths, some with characters Windows does not allow in a path. */
@EnabledOnOs(OS.LINUX)
class LinuxStartAtLoginTest {

    @TempDir
    Path config;

    private LinuxStartAtLogin startAtLogin(String program) {
        return new LinuxStartAtLogin(config.resolve("autostart"), "car-charge-planner.desktop", Path.of(program),
                "Car charge planner");
    }

    private String entry() throws Exception {
        return Files.readString(config.resolve("autostart/car-charge-planner.desktop"));
    }

    @Test
    void switchedOnTheEntryStartsTheLauncherHiddenAfterThePanel() throws Exception {
        startAtLogin("/opt/car-charge-planner/bin/car-charge-planner").switchOn();
        assertThat(entry()).isEqualTo("""
                [Desktop Entry]
                Type=Application
                Name=Car charge planner
                Exec="/opt/car-charge-planner/bin/car-charge-planner" --background
                Terminal=false
                X-GNOME-Autostart-enabled=true
                X-KDE-autostart-after=panel
                """);
    }

    @Test
    void theStateFollowsTheEntry() throws Exception {
        var startAtLogin = startAtLogin("/opt/car-charge-planner/bin/car-charge-planner");
        assertThat(startAtLogin.isOn()).isFalse();
        startAtLogin.switchOn();
        assertThat(startAtLogin.isOn()).isTrue();
        startAtLogin.switchOff();
        assertThat(startAtLogin.isOn()).isFalse();
        assertThat(config.resolve("autostart/car-charge-planner.desktop")).doesNotExist();
        startAtLogin.switchOff();
    }

    @Test
    void aPathWithCharactersTheEntryGivesAMeaningIsEscaped() throws Exception {
        startAtLogin("/home/me/My \"apps\" $HOME `x` 100%/a\\b/car-charge-planner").switchOn();
        assertThat(entry()).contains(
                "Exec=\"/home/me/My \\\\\"apps\\\\\" \\\\$HOME \\\\`x\\\\` 100%%/a\\\\\\\\b/car-charge-planner\" --background\n");
    }
}
