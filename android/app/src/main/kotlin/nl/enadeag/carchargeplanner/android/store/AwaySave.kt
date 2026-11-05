package nl.enadeag.carchargeplanner.android.store

import nl.enadeag.carchargeplanner.engine.away.OneOffAwayPeriod

/** What adding or removing a one-off away period came to. */
sealed interface AwaySave {

    /**
     * The change is stored; the next plan leaves the stored periods free.
     *
     * @property periods the stored periods after the change, in order of their start
     */
    data class Saved(val periods: List<OneOffAwayPeriod>) : AwaySave

    /**
     * The change could not be stored, as the file could not be read whole or written, or was written by a newer
     * version of the app; the stored periods stay as they were.
     *
     * @property detail the system's or the engine's words, in English
     */
    data class Failed(val detail: String) : AwaySave
}
