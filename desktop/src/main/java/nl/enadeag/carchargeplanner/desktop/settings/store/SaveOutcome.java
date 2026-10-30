package nl.enadeag.carchargeplanner.desktop.settings.store;

/** What pressing Save came to. */
public sealed interface SaveOutcome {

    /** Everything was stored. */
    record Saved() implements SaveOutcome {
    }

    /**
     * The system refused to start the app at login, or to stop it; nothing after it was stored.
     *
     * @param detail the system's reason, as it gave it
     */
    record StartAtLoginRefused(String detail) implements SaveOutcome {
    }

    /**
     * The API keys were not stored, because the system keychain is locked and was not unlocked when the system asked;
     * nothing after them was stored either.
     */
    record KeychainLocked() implements SaveOutcome {
    }

    /**
     * The API keys could not be stored, in the keychain or in their file, for another reason than a locked keychain;
     * nothing after them was stored. The reason, in English, is in the log.
     */
    record KeysNotSaved() implements SaveOutcome {
    }

    /**
     * A file could not be written, or the work failed; what was stored before that stays stored.
     *
     * @param detail the system's error, as it gave it
     */
    record Failed(String detail) implements SaveOutcome {
    }
}
