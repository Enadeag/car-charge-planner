package nl.enadeag.carchargeplanner.android.store

/** What a save of the battery level the owner entered came to. */
sealed interface BatteryLevelSave {

    /** The level is stored; the next plan starts from it. */
    data object Saved : BatteryLevelSave

    /**
     * The level could not be stored, as the file could not be written or was written by a newer version of the app;
     * the level stored before stays.
     *
     * @property detail the system's or the engine's words, in English
     */
    data class Failed(val detail: String) : BatteryLevelSave
}
