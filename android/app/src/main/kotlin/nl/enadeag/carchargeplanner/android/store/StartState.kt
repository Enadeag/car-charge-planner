package nl.enadeag.carchargeplanner.android.store

import nl.enadeag.carchargeplanner.engine.config.ConfigProblem
import nl.enadeag.carchargeplanner.engine.config.EngineConfig

/** What the settings file holds as the app starts: what the first screen shows. */
sealed interface StartState {

    /** The file is being read; the screen shows that it waits. */
    data object Reading : StartState

    /**
     * The file holds settings the app can use.
     *
     * @property config the whole config, every value present and valid
     */
    data class Ready(val config: EngineConfig) : StartState

    /** The file holds no settings the app can use: set-up, "Set up again" or "update the app". */
    sealed interface WithoutSettings : StartState

    /** There is no file: the owner goes through set-up (see "Not set up" in CONTEXT.md). */
    data object NotSetUp : WithoutSettings

    /**
     * The file is damaged or cannot be read (see "Unusable config" in CONTEXT.md): its details, and "Set up again".
     *
     * @property fileName the file's name, such as `engine.json`
     * @property details the engine's own words, in English, such as the setting and what is wrong with it
     */
    data class Unusable(val fileName: String, val details: String) : WithoutSettings

    /**
     * A newer version of the app wrote the file: the owner updates the app. The file is never set aside or written.
     *
     * @property details the engine's own words, in English: the file's version and the newest this version reads
     */
    data class NewerVersion(val details: String) : WithoutSettings

    companion object {

        /**
         * The state for a settings file the engine could not use.
         *
         * @param problem why the engine could not use it
         * @return not set up for a missing file, newer version, or unusable with the file's name and the details
         */
        fun of(problem: ConfigProblem): WithoutSettings = when (problem.kind()) {
            ConfigProblem.Kind.MISSING -> NotSetUp
            ConfigProblem.Kind.NEWER_VERSION -> NewerVersion(problem.detail())
            ConfigProblem.Kind.UNREADABLE, ConfigProblem.Kind.INVALID ->
                Unusable(problem.file().fileName.toString(), problem.detail())
        }
    }
}
