package nl.enadeag.carchargeplanner.desktop.settings.about;

import java.nio.file.Path;

/**
 * What the About page tells besides its fixed texts.
 *
 * @param version the app's version, such as "0.3.0"
 * @param configFile the engine's config file
 * @param appSettingsFile the desktop app's settings file
 * @param dataFolder the folder of the forecast cache, the away periods entered by hand and the battery level
 * @param logFile the log file the app writes to now, which a bug report may quote
 * @param keysFile the file the API keys are kept in when there is no usable keychain
 */
public record AboutFacts(String version, Path configFile, Path appSettingsFile, Path dataFolder, Path logFile,
        Path keysFile) {
}
