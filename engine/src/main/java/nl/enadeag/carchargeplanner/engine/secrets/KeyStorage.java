package nl.enadeag.carchargeplanner.engine.secrets;

/** Where the API keys are kept now, so the app can tell the owner. */
public enum KeyStorage {
    /** The system's keychain. */
    KEYCHAIN,
    /** The system's keychain, which is locked: the keys can be read once the owner unlocks it. */
    LOCKED_KEYCHAIN,
    /**
     * No usable keychain, the owner chose not to unlock it, or the file could not be moved into it: a file readable
     * only by the owner. Only with {@link KeyFallback#PRIVATE_FILE}.
     */
    FILE,
    /**
     * No usable keychain, and no file to keep the keys in: they can be neither read nor saved until the keychain can
     * be used again. Only with {@link KeyFallback#NONE}, so never on the desktop.
     */
    UNAVAILABLE
}
