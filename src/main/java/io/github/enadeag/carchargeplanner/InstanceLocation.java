package io.github.enadeag.carchargeplanner;

import java.nio.file.Path;
import java.util.Map;

/** Where the single-instance socket's private folder is, on each system. */
final class InstanceLocation {

    private InstanceLocation() {
    }

    /**
     * The folder for the instance socket.
     *
     * @param osName the system's name, as the {@code os.name} property gives it
     * @param environment the environment variables; Windows needs {@code LOCALAPPDATA}, Linux may give
     *     {@code XDG_RUNTIME_DIR}, which is ignored when empty or relative
     * @param temporary the system's folder for temporary files, for Linux without a usable {@code XDG_RUNTIME_DIR}
     * @param userName the user's login name, which tells users' folders apart in the shared temporary folder
     * @return on Windows the app's folder in the user's local application data, which only the user, the system and
     *     administrators may open; on Linux a folder of the app's in the user's runtime folder, else in the temporary
     *     folder. It need not exist yet.
     */
    static Path folder(String osName, Map<String, String> environment, Path temporary, String userName) {
        if (osName.startsWith("Windows")) {
            return Path.of(environment.get("LOCALAPPDATA")).resolve(ConfigLocation.APP_FOLDER);
        }
        var runtime = environment.getOrDefault("XDG_RUNTIME_DIR", "");
        if (runtime.isEmpty() || !Path.of(runtime).isAbsolute()) {
            return temporary.resolve(ConfigLocation.APP_FOLDER + "-" + userName);
        }
        return Path.of(runtime).resolve(ConfigLocation.APP_FOLDER);
    }
}
