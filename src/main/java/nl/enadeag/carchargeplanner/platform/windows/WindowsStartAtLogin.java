package nl.enadeag.carchargeplanner.platform.windows;

import nl.enadeag.carchargeplanner.desktop.system.StartAtLogin;
import java.io.IOException;
import java.nio.file.Path;

/** Start at login on Windows: a value in the user's Run key, which Windows runs when the user signs in. */
public final class WindowsStartAtLogin implements StartAtLogin {

    private final RunKey runKey;
    private final String name;
    private final Path program;

    /**
     * Makes the value's keeper; nothing is read or written yet.
     *
     * @param runKey the user's Run key
     * @param name the value's name, which Windows shows in its list of programs started at login, such as
     *     "Car charge planner"; not empty
     * @param program the app's launcher, an absolute path such as
     *     {@code C:\Users\me\AppData\Local\Programs\car-charge-planner\car-charge-planner.exe}
     */
    public WindowsStartAtLogin(RunKey runKey, String name, Path program) {
        this.runKey = runKey;
        this.name = name;
        this.program = program;
    }

    @Override
    public boolean isOn() throws IOException {
        return runKey.get(name).isPresent();
    }

    @Override
    public void switchOn() throws IOException {
        // always quoted: Windows reads an unquoted C:\Program Files\app.exe as the program C:\Program
        runKey.set(name, "\"" + program + "\" " + BACKGROUND);
    }

    @Override
    public void switchOff() throws IOException {
        runKey.delete(name);
    }
}
