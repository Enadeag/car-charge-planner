package io.github.enadeag.carchargeplanner;

import java.nio.file.Path;
import java.util.Map;

/** Where the engine's config file is, on each system: the folder the user's settings usually go to. */
final class ConfigLocation {

    static final String APP_FOLDER = "car-charge-planner";
    static final String FILE_NAME = "engine.json";

    private ConfigLocation() {
    }

    static Path defaultPath(String osName, Map<String, String> environment, Path home) {
        Path folder;
        if (osName.startsWith("Windows")) {
            folder = Path.of(environment.get("APPDATA"));
        } else {
            var xdg = environment.getOrDefault("XDG_CONFIG_HOME", "");
            // the XDG specification says to ignore a relative path
            folder = xdg.isEmpty() || !Path.of(xdg).isAbsolute() ? home.resolve(".config") : Path.of(xdg);
        }
        return folder.resolve(APP_FOLDER).resolve(FILE_NAME);
    }
}
