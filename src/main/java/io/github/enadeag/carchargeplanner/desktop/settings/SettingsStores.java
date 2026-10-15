package io.github.enadeag.carchargeplanner.desktop.settings;

import io.github.enadeag.carchargeplanner.desktop.AppSettingsFile;
import io.github.enadeag.carchargeplanner.desktop.StartAtLogin;
import io.github.enadeag.carchargeplanner.engine.Engine;
import java.util.Optional;

/**
 * Where the settings are kept: the engine keeps its own, the desktop app keeps its own, and the system keeps start at
 * login.
 *
 * @param engine the engine, which reads, checks and writes its config file
 * @param appSettings the desktop app's settings file
 * @param startAtLogin the system's start at login; empty when this copy of the app cannot start at login
 */
public record SettingsStores(Engine engine, AppSettingsFile appSettings, Optional<StartAtLogin> startAtLogin) {
}
