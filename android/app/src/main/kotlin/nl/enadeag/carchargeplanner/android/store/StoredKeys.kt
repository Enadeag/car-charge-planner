package nl.enadeag.carchargeplanner.android.store

import nl.enadeag.carchargeplanner.engine.secrets.Secrets

/** The API keys as the keychain gave them, or that it could not. */
sealed interface StoredKeys {

    /**
     * The keys could be read.
     *
     * @property keys every provider's secret settings, possibly none
     */
    data class Read(val keys: Secrets) : StoredKeys

    /**
     * The keychain could not give them; the owner may enter them again, and a key left empty stays as it is stored.
     * The engine's reason, in English, is in the log.
     */
    data object Unreadable : StoredKeys
}
