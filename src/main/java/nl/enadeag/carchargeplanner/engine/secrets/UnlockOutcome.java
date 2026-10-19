package nl.enadeag.carchargeplanner.engine.secrets;

/** What came of asking the owner to unlock the keychain with the system's own prompt. */
public enum UnlockOutcome {
    /** The keychain is unlocked now. */
    UNLOCKED,
    /** The owner dismissed the prompt: they chose not to unlock it. */
    DISMISSED,
    /** No answer in time, or the app stopped waiting, such as when the window closed: it stays locked. */
    NOT_ANSWERED
}
