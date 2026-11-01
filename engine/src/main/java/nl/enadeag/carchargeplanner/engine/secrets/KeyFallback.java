package nl.enadeag.carchargeplanner.engine.secrets;

/** Where {@link ApiKeys} keeps the API keys when the system keychain cannot be used. */
public enum KeyFallback {
    /**
     * In the private file, {@code secrets.json}, readable only by the owner; for a desktop, where a system may have no
     * keychain the app can use.
     */
    PRIVATE_FILE,
    /**
     * Nowhere: the keys are kept only in the keychain, so they never end up unencrypted, and a load or a save that
     * cannot use it fails; for Android, whose keychain is the app's own store, encrypted with a Keystore key.
     */
    NONE
}
