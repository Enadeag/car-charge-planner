package nl.enadeag.carchargeplanner.android.ui

import java.nio.file.Path
import nl.enadeag.carchargeplanner.engine.config.ConfigFile
import nl.enadeag.carchargeplanner.engine.config.EngineConfig
import nl.enadeag.carchargeplanner.engine.store.FilePrivacy
import nl.enadeag.carchargeplanner.engine.store.PrivateFiles

/** A screen that breaks `AppArchitectureTest`'s rule on purpose, to show the rule finds it. */
internal class ScreenThatReadsTheSettingsFile {

    /** Reads the settings file on whatever thread calls it, which a screen must not. */
    fun settings(): EngineConfig = ConfigFile(Path.of("engine.json"), PrivateFiles(FilePrivacy.NONE)).read()
}
