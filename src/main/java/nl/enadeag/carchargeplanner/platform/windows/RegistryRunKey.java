package nl.enadeag.carchargeplanner.platform.windows;

import java.io.IOException;
import java.util.Optional;

/**
 * The user's real Run key, or for tests a key of the same kind, reached through {@link Advapi32}. Not
 * {@code reg.exe}: Java quotes the arguments of a started program in its own way, and the command line in the value
 * has quotes of its own, which would have to survive both.
 */
public final class RegistryRunKey implements RunKey {

    /** The Run key's path below HKEY_CURRENT_USER. */
    public static final String RUN = "Software\\Microsoft\\Windows\\CurrentVersion\\Run";

    private final String key;

    /**
     * Makes the key; nothing is read or written yet. Only for Windows.
     *
     * @param key the key's path below HKEY_CURRENT_USER: {@link #RUN}, or another key for a test, which leaves the
     *     programs started at login alone; made when a value is first written
     */
    public RegistryRunKey(String key) {
        this.key = key;
    }

    @Override
    public Optional<String> get(String name) throws IOException {
        return Advapi32.readText(key, name);
    }

    @Override
    public void set(String name, String commandLine) throws IOException {
        Advapi32.writeText(key, name, commandLine);
    }

    @Override
    public void delete(String name) throws IOException {
        Advapi32.deleteValue(key, name);
    }
}
