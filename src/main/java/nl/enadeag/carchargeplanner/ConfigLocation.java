package nl.enadeag.carchargeplanner;

import java.nio.file.Path;
import java.util.Map;

/** Where the engine's config file is, on each system: the folder the user's settings usually go to. */
final class ConfigLocation {

    /** The folder of the app inside the system's config folder. */
    static final String APP_FOLDER = "car-charge-planner";
    /** The name of the config file inside the app's folder. */
    static final String FILE_NAME = "engine.json";

    private ConfigLocation() {
    }

    /**
     * Where the config file is when the user names none.
     *
     * @param os the system the app runs on; Windows has a folder of its own, the others share the XDG one
     * @param environment the environment variables; {@code APPDATA} must be present on Windows, {@code XDG_CONFIG_HOME}
     *     is optional elsewhere and ignored when empty or relative
     * @param home the user's home folder, used where no config folder is set
     * @return the path of {@code engine.json} in the app's folder; the file need not exist
     * @throws NullPointerException on Windows when {@code APPDATA} is missing from the environment
     */
    static Path defaultPath(Os os, Map<String, String> environment, Path home) {
        return systemFolder(os, environment, home).resolve(APP_FOLDER).resolve(FILE_NAME);
    }

    /**
     * The folder the user's settings of all programs go in, such as {@code ~/.config}.
     *
     * @param os the system the app runs on; Windows has a folder of its own, the others share the XDG one
     * @param environment the environment variables; {@code APPDATA} must be present on Windows, {@code XDG_CONFIG_HOME}
     *     is optional elsewhere and ignored when empty or relative
     * @param home the user's home folder, used where no config folder is set
     * @return the folder; it need not exist
     * @throws NullPointerException on Windows when {@code APPDATA} is missing from the environment
     */
    static Path systemFolder(Os os, Map<String, String> environment, Path home) {
        if (os == Os.WINDOWS) {
            return Path.of(environment.get("APPDATA"));
        }
        var xdg = environment.getOrDefault("XDG_CONFIG_HOME", "");
        // the XDG specification says to ignore a relative path
        return xdg.isEmpty() || !Path.of(xdg).isAbsolute() ? home.resolve(".config") : Path.of(xdg);
    }
}
