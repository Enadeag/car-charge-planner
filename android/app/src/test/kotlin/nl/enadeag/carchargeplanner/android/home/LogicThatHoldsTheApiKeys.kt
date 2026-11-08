package nl.enadeag.carchargeplanner.android.home

import nl.enadeag.carchargeplanner.engine.secrets.ApiKeys
import nl.enadeag.carchargeplanner.engine.secrets.KeyStorage

/** Logic outside the screens that breaks `AppArchitectureTest`'s rule on the engine on purpose. */
internal class LogicThatHoldsTheApiKeys(private val keys: ApiKeys) {

    /** Asks the keychain on whatever thread calls it, which may block for about a second. */
    fun storage(): KeyStorage = keys.storage()
}
