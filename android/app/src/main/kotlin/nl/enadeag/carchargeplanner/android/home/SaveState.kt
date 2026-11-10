package nl.enadeag.carchargeplanner.android.home

/** Where an entry's Save is: the trip entry's, the away entry's or the battery entry's. */
sealed interface SaveState {

    /** Not saving, and no save failed since the entry opened or the last save. */
    data object Idle : SaveState

    /** What was entered is being saved; Save, Cancel, Close and Back wait. */
    data object Saving : SaveState

    /**
     * What was entered could not be stored; the entry keeps it.
     *
     * @property detail the system's or the engine's words, in English
     */
    data class Failed(val detail: String) : SaveState
}
