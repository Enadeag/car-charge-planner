package nl.enadeag.carchargeplanner.host;

import nl.enadeag.carchargeplanner.instance.Owner;
import java.io.IOException;
import java.lang.System.Logger.Level;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
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
 * @param launcher the path of jpackage's launcher executable, which start at login and the menu entry start; empty
 *     when the app does not run from it, such as from Maven
 * @param terminal whether the app runs in a terminal, which shows what it logs; false when it was started from the
 *     desktop, or by Windows' launcher, which has no console
 * @param flatpak whether the app runs in a Flatpak sandbox (ADR 0008), which puts it in the menu itself and starts
 *     it at login through the desktop's Background portal
 */
public record Host(Os os, Map<String, String> environment, Path home, Path temporary, Owner owner,
        Optional<Path> launcher, boolean terminal, boolean flatpak) {

    private static final System.Logger LOG = System.getLogger(Host.class.getName());
    /** The file Flatpak puts in every sandbox it runs. */
    private static final Path FLATPAK_INFO = Path.of("/.flatpak-info");
    private static final String RUNTIME_DIR = "XDG_RUNTIME_DIR";

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
     * @return what the system properties and the environment say now; in a Flatpak sandbox with the runtime folder
     *     that every run of the app shares, see {@link #sharedRuntimeFolder(Map)}
     */
    public static Host current() {
        var os = Os.current();
        var launcher = System.getProperty("jpackage.app-path");
        var console = System.console();
        var flatpak = inFlatpak(System.getenv(), FLATPAK_INFO);
        return new Host(os, flatpak ? sharedRuntimeFolder(System.getenv()) : System.getenv(),
                Path.of(System.getProperty("user.home")), Path.of(System.getProperty("java.io.tmpdir")), owner(os),
                Optional.ofNullable(launcher).map(Path::of).map(Path::toAbsolutePath),
                console != null && console.isTerminal(), flatpak);
    }

    /**
     * Whether the app runs in a Flatpak sandbox, told the way Flatpak documents.
     *
     * @param environment the environment variables, where Flatpak sets {@code FLATPAK_ID}
     * @param flatpakInfo the file Flatpak puts in every sandbox, {@code /.flatpak-info}
     * @return true when either is there
     */
    static boolean inFlatpak(Map<String, String> environment, Path flatpakInfo) {
        return environment.containsKey("FLATPAK_ID") || Files.exists(flatpakInfo);
    }

    /**
     * The environment with the runtime folder the app uses in a Flatpak sandbox. There {@code XDG_RUNTIME_DIR} is
     * the sandbox's own, new for each run, so a second start would not find the running app's socket, and the panel
     * and the notification server outside could not read the icons. Its folder {@code app/<Flatpak ID>} is the one
     * that every run of the app and the desktop share, at the same path.
     *
     * @param environment the environment variables in the sandbox
     * @return the environment with {@code XDG_RUNTIME_DIR} in that folder; unchanged when it is not an absolute path,
     *     so the app falls back as without one
     */
    static Map<String, String> sharedRuntimeFolder(Map<String, String> environment) {
        var runtime = environment.getOrDefault(RUNTIME_DIR, "");
        if (runtime.isEmpty() || !Path.of(runtime).isAbsolute()) {
            return environment;
        }
        var shared = new HashMap<>(environment);
        shared.put(RUNTIME_DIR, Path.of(runtime).resolve("app").resolve(AppName.FLATPAK_ID).toString());
        return shared;
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
