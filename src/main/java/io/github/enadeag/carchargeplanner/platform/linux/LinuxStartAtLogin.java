package io.github.enadeag.carchargeplanner.platform.linux;

import io.github.enadeag.carchargeplanner.desktop.StartAtLogin;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Start at login on Linux: a desktop entry in the user's autostart folder, which desktops that follow the XDG
 * Autostart specification run at login.
 */
public final class LinuxStartAtLogin implements StartAtLogin {

    private final Path file;
    private final Path program;
    private final String name;

    /**
     * Makes the entry's keeper; nothing is read or written yet.
     *
     * @param autostartFolder the user's autostart folder, {@code $XDG_CONFIG_HOME/autostart}; it is made when missing
     * @param fileName the entry's file name, such as "car-charge-planner.desktop"
     * @param program the app's launcher, an absolute path; it is quoted in the entry, so it may hold spaces, but no
     *     line break
     * @param name the app's name that the desktop shows for the entry, such as in its list of programs started at login
     */
    public LinuxStartAtLogin(Path autostartFolder, String fileName, Path program, String name) {
        this.file = autostartFolder.resolve(fileName);
        this.program = program;
        this.name = name;
    }

    @Override
    public boolean isOn() {
        return Files.exists(file);
    }

    @Override
    public void switchOn() throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, entry());
    }

    @Override
    public void switchOff() throws IOException {
        Files.deleteIfExists(file);
    }

    private String entry() {
        return """
                [Desktop Entry]
                Type=Application
                Name=%s
                Exec=%s %s
                Terminal=false
                X-GNOME-Autostart-enabled=true
                X-KDE-autostart-after=panel
                """.formatted(name, quoted(program.toString()), BACKGROUND);
    }

    /**
     * Quoted as the Desktop Entry specification asks: in double quotes, with {@code " ` $ \} escaped by a backslash and
     * each backslash then doubled once more for the string value, and {@code %} doubled so it is no field code.
     */
    private static String quoted(String argument) {
        if (argument.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("the program's path holds a control character: " + argument);
        }
        var escaped = argument.replaceAll("([\"`$\\\\])", "\\\\$1").replace("\\", "\\\\").replace("%", "%%");
        return "\"" + escaped + "\"";
    }
}
