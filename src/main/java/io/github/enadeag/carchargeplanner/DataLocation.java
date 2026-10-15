package io.github.enadeag.carchargeplanner;

import java.nio.file.Path;
import java.util.Map;

/** Where the engine keeps the data it makes itself, such as the forecast cache, on each system. */
final class DataLocation {

    private DataLocation() {
    }

    static Path defaultFolder(String osName, Map<String, String> environment, Path home) {
        Path folder;
        if (osName.startsWith("Windows")) {
            folder = Path.of(environment.get("LOCALAPPDATA")); // stays on this computer, unlike the roaming folder
        } else {
            var xdg = environment.getOrDefault("XDG_DATA_HOME", "");
            // the XDG specification says to ignore a relative path
            folder = xdg.isEmpty() || !Path.of(xdg).isAbsolute() ? home.resolve(".local/share") : Path.of(xdg);
        }
        return folder.resolve(ConfigLocation.APP_FOLDER);
    }
}
