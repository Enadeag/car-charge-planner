package nl.enadeag.carchargeplanner.engine.secrets;

/** Where the API keys are kept now, so the app can tell the owner. */
public enum KeyStorage {
    /** The system's keychain. */
    KEYCHAIN,
    /** The system's keychain, which is locked: the keys can be read once the owner unlocks it. */
    LOCKED_KEYCHAIN,
    /**
     * No usable keychain, the owner chose not to unlock it, or the file could not be moved into it: a file readable
     * only by the owner.
     */
    FILE
}
