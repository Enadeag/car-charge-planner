package nl.enadeag.carchargeplanner.android.store

/** What a save of set-up's Finish or of a Settings screen came to, which the next screen shows. */
sealed interface SaveOutcome {

    /** Everything was stored, or nothing differed from what was. */
    data object Saved : SaveOutcome

    /**
     * The API keys could not be stored, so nothing after them was either. The engine's reason, in English, is in the
     * log.
     */
    data object KeysNotSaved : SaveOutcome

    /**
     * The settings file, read again before the change, holds no settings the change can go into, or one of a newer
     * version that is never written over; nothing of the settings was written.
     *
     * @property state what the file holds now
     */
    data class SettingsUnusable(val state: StartState.WithoutSettings) : SaveOutcome

    /**
     * The settings file could not be written, the change no longer fits the file as it is now, or the work failed;
     * what was stored before stays.
     *
     * @property detail the system's or the engine's words, in English
     */
    data class Failed(val detail: String) : SaveOutcome
}
