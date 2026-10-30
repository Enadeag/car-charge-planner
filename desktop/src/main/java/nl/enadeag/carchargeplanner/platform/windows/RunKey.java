package nl.enadeag.carchargeplanner.platform.windows;

import java.io.IOException;
import java.util.Optional;

/**
 * The text values of the user's Run key, {@code HKEY_CURRENT_USER\Software\Microsoft\Windows\CurrentVersion\Run}, whose
 * command lines Windows runs when the user signs in. Tests stand in for the real registry with one of their own.
 */
public interface RunKey {

    /**
     * Reads a value.
     *
     * @param name the value's name, such as "Car charge planner"; not empty
     * @return the command line it holds, or empty when there is no such value
     * @throws IOException when the registry refuses to be read
     */
    Optional<String> get(String name) throws IOException;

    /**
     * Writes a value, replacing the one of that name.
     *
     * @param name the value's name; not empty
     * @param commandLine the command line Windows is to run at sign-in, with the program quoted where it has spaces
     * @throws IOException when the registry refuses the value
     */
    void set(String name, String commandLine) throws IOException;

    /**
     * Removes a value; does nothing when there is none of that name.
     *
     * @param name the value's name; not empty
     * @throws IOException when the registry refuses to remove it
     */
    void delete(String name) throws IOException;
}
