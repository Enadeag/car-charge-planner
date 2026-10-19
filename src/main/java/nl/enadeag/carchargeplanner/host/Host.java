package nl.enadeag.carchargeplanner.host;

import nl.enadeag.carchargeplanner.instance.Owner;
import java.io.IOException;
import java.lang.System.Logger.Level;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

/**
 * What the app needs to know of the system and the user it runs as, read once as it starts, so that no other part
 * reads a system property or the environment.
 *
 * @param os the operating system
 * @param environment the environment variables
 * @param home the user's home folder
 * @param temporary the system's folder for temporary files
 * @param owner who must own the private folders and the instance socket: the user the app runs as where files have
 *     an owner and a mode (Linux); unchecked on other systems, and when the user could not be told
 * @param launcher the program jpackage's launcher started, which start at login and the menu entry start; empty
 *     when the app does not run from it, such as from Maven
 * @param terminal whether the app runs in a terminal, which shows what it logs; false when it was started from the
 *     desktop, or by Windows' launcher, which has no console
 */
public record Host(Os os, Map<String, String> environment, Path home, Path temporary, Owner owner,
        Optional<Path> launcher, boolean terminal) {

    private static final System.Logger LOG = System.getLogger(Host.class.getName());

    /**
     * Copies the environment, so it stays as it was read.
     *
     * @throws IllegalArgumentException when a value is null
     */
    public Host {
        if (os == null || environment == null || home == null || temporary == null || owner == null) {
            throw new IllegalArgumentException("every part of the host must be given");
        }
        environment = Map.copyOf(environment);
    }

    /**
     * The system this JVM runs on, and its user.
     *
     * @return what the system properties and the environment say now
     */
    public static Host current() {
        var os = Os.current();
        var launcher = System.getProperty("jpackage.app-path");
        var console = System.console();
        return new Host(os, System.getenv(), Path.of(System.getProperty("user.home")),
                Path.of(System.getProperty("java.io.tmpdir")), owner(os),
                Optional.ofNullable(launcher).map(Path::of).map(Path::toAbsolutePath),
                console != null && console.isTerminal());
    }

    /**
     * Linux: the owner of the process's own entry in /proc is the user it runs as. Unchecked on Windows: see
     * SingleInstance. When /proc cannot tell, the app runs on without checking owners, rather than not at all.
     */
    private static Owner owner(Os os) {
        if (os != Os.LINUX) {
            return new Owner.Unchecked();
        }
        try {
            return new Owner.User(Files.getOwner(Path.of("/proc/self")));
        } catch (IOException e) {
            LOG.log(Level.WARNING, "cannot tell which user this app runs as; private folders are not checked", e);
            return new Owner.Unchecked();
        }
    }
}
