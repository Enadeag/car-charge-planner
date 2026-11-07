package nl.enadeag.carchargeplanner.android.store

import nl.enadeag.carchargeplanner.engine.trip.Trip

/** What adding, changing or removing a trip came to. */
sealed interface TripSave {

    /**
     * The change is stored; the next plan follows the stored trips.
     *
     * @property trips the stored trips after the change, in order of their departure, also those that returned in the
     *     last day
     */
    data class Saved(val trips: List<Trip>) : TripSave

    /**
     * The change could not be stored, as the file could not be read whole or written, or was written by a newer
     * version of the app; the stored trips stay as they were.
     *
     * @property detail the system's or the engine's words, in English
     */
    data class Failed(val detail: String) : TripSave
}
