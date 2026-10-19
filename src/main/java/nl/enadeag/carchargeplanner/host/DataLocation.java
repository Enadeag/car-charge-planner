package nl.enadeag.carchargeplanner.host;

import java.nio.file.Path;
import java.util.Map;

/** Where the engine keeps the data it makes itself, such as the forecast cache, on each system. */
public final class DataLocation {

    private DataLocation() {
    }

    /**
     * The app's data folder on this system.
     *
     * @param os the system the app runs on
     * @param environment the environment variables; Windows needs {@code LOCALAPPDATA}, Linux may give
     *     {@code XDG_DATA_HOME}
     * @param home the user's home folder, for when Linux gives no usable {@code XDG_DATA_HOME}
     * @return the app's own folder inside the user's data folder; it need not exist yet
     */
    public static Path defaultFolder(Os os, Map<String, String> environment, Path home) {
        return systemFolder(os, environment, home).resolve(ConfigLocation.APP_FOLDER);
    }

    /**
     * The folder the user's data of all programs goes in, such as {@code ~/.local/share}.
     *
     * @param os the system the app runs on
     * @param environment the environment variables; Windows needs {@code LOCALAPPDATA}, Linux may give
     *     {@code XDG_DATA_HOME}, which is ignored when empty or relative
     * @param home the user's home folder, for when Linux gives no usable {@code XDG_DATA_HOME}
     * @return the folder; it need not exist yet
     */
    public static Path systemFolder(Os os, Map<String, String> environment, Path home) {
        if (os == Os.WINDOWS) {
            return Path.of(environment.get("LOCALAPPDATA")); // stays on this computer, unlike the roaming folder
        }
        var xdg = environment.getOrDefault("XDG_DATA_HOME", "");
        // the XDG specification says to ignore a relative path
        return xdg.isEmpty() || !Path.of(xdg).isAbsolute() ? home.resolve(".local/share") : Path.of(xdg);
    }
}
