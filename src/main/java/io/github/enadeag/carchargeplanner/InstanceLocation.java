package io.github.enadeag.carchargeplanner;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** Where the single-instance guard's private folder, for its lock and its socket, is on each system. */
final class InstanceLocation {

    /** The folder in the app's data folder for when Linux gives no usable runtime folder. */
    static final String DATA_SUBFOLDER = "run";

    private InstanceLocation() {
    }

    /**
     * The folder for the instance lock and socket. Every start of the app by the same user must get the same folder.
     *
     * @param os the system the app runs on
     * @param environment the environment variables; Windows needs {@code LOCALAPPDATA}, Linux may give
     *     {@code XDG_RUNTIME_DIR}, which is used only when it is an absolute path to a folder this user may write in,
     *     and {@code XDG_DATA_HOME}
     * @param home the user's home folder, for when Linux gives neither a usable {@code XDG_RUNTIME_DIR} nor
     *     {@code XDG_DATA_HOME}
     * @return on Windows the app's folder in the user's local application data, which only the user, the system and
     *     administrators may open; on Linux a folder of the app's in the user's runtime folder, else the folder
     *     {@value #DATA_SUBFOLDER} in the app's data folder, such as {@code ~/.local/share/car-charge-planner/run}:
     *     never one in a shared folder such as {@code /tmp}, where another user could make it first. It need not exist
     *     yet.
     */
    static Path folder(Os os, Map<String, String> environment, Path home) {
        if (os == Os.WINDOWS) {
            return Path.of(environment.get("LOCALAPPDATA")).resolve(ConfigLocation.APP_FOLDER);
        }
        var runtime = environment.getOrDefault("XDG_RUNTIME_DIR", "");
        if (runtime.isEmpty() || !Path.of(runtime).isAbsolute() || !Files.isDirectory(Path.of(runtime))
                || !Files.isWritable(Path.of(runtime))) {
            return DataLocation.defaultFolder(os, environment, home).resolve(DATA_SUBFOLDER);
        }
        return Path.of(runtime).resolve(ConfigLocation.APP_FOLDER);
    }
}
