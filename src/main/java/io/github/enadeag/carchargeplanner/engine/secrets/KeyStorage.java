package io.github.enadeag.carchargeplanner.engine.secrets;

/** Where the API keys are kept now, so the app can tell the owner. */
public enum KeyStorage {
    KEYCHAIN,
    /** No usable keychain, or the file could not be moved into it: a file readable only by the owner. */
    FILE
}
