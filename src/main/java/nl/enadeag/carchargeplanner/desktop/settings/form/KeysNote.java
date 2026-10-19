package nl.enadeag.carchargeplanner.desktop.settings.form;

import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.desktop.text.TextKey;
import nl.enadeag.carchargeplanner.engine.secrets.KeyStorage;
import nl.enadeag.carchargeplanner.engine.secrets.SecretsProblem;
import java.nio.file.Path;
import java.util.Optional;

/** What the forecast page says about where the API keys are kept, and why they could not be read. */
public final class KeysNote {

    private KeysNote() {
    }

    /**
     * The note, all in the owner's language: the engine's own words about the problem are English, for the log.
     *
     * @param storage where the keys are kept now
     * @param problem the kind of problem that kept the keys from being read; empty when they could be read
     * @param keysFile the file the keys are kept in when there is no usable keychain
     * @param messages the texts, in the owner's language
     * @return where the keys are kept, after the problem when there is one; a locked keychain is the reason itself,
     *     so the note then only says that
     */
    public static String of(KeyStorage storage, Optional<SecretsProblem.Kind> problem, Path keysFile,
            Messages messages) {
        if (problem.isEmpty()) {
            return where(storage, keysFile, messages);
        }
        return switch (problem.get()) {
            case LOCKED -> where(KeyStorage.LOCKED_KEYCHAIN, keysFile, messages);
            case FAILED -> storage == KeyStorage.LOCKED_KEYCHAIN ? where(storage, keysFile, messages)
                    : messages.text(TextKey.KEYS_PROBLEM) + " " + where(storage, keysFile, messages);
        };
    }

    private static String where(KeyStorage storage, Path keysFile, Messages messages) {
        return switch (storage) {
            case KEYCHAIN -> messages.text(TextKey.KEYS_IN_KEYCHAIN);
            case LOCKED_KEYCHAIN -> messages.text(TextKey.KEYS_IN_LOCKED_KEYCHAIN);
            case FILE -> messages.text(TextKey.KEYS_IN_FILE, keysFile.toString());
        };
    }
}
