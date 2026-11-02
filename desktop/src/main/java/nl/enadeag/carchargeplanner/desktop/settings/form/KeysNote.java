package nl.enadeag.carchargeplanner.desktop.settings.form;

import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.desktop.text.TextKey;
import nl.enadeag.carchargeplanner.engine.secrets.KeyStorage;
import nl.enadeag.carchargeplanner.engine.secrets.SecretsProblem;
import java.nio.file.Path;

/** What the forecast page says about where the API keys are kept, and why they could not be read. */
public final class KeysNote {

    private KeysNote() {
    }

    /**
     * The note for keys that could be read, in the owner's language.
     *
     * @param storage where the keys are kept now; never {@link KeyStorage#UNAVAILABLE}: the desktop always has the file
     * @param keysFile the file the keys are kept in when there is no usable keychain
     * @param messages the texts, in the owner's language
     * @return where the keys are kept
     * @throws IllegalArgumentException when the storage is {@link KeyStorage#UNAVAILABLE}
     */
    public static String of(KeyStorage storage, Path keysFile, Messages messages) {
        return where(storage, keysFile, messages);
    }

    /**
     * The note for keys that could not be read, all in the owner's language: the engine's own words about the problem
     * are English, for the log.
     *
     * @param storage where the keys are kept now; never {@link KeyStorage#UNAVAILABLE}: the desktop always has the file
     * @param problem the kind of problem that kept the keys from being read
     * @param keysFile the file the keys are kept in when there is no usable keychain
     * @param messages the texts, in the owner's language
     * @return where the keys are kept, after the problem; a locked keychain is the reason itself, so the note then
     *     only says that
     * @throws IllegalArgumentException when the storage is {@link KeyStorage#UNAVAILABLE} and the problem is not
     *     that the keychain is locked
     */
    public static String of(KeyStorage storage, SecretsProblem.Kind problem, Path keysFile, Messages messages) {
        return switch (problem) {
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
            case UNAVAILABLE -> throw notOnTheDesktop(storage);
        };
    }

    /**
     * The mistake of asking the desktop to say where keys are kept that only an app without the file as fallback has.
     *
     * @param storage where the keys are said to be kept: {@link KeyStorage#UNAVAILABLE}
     * @return the exception to throw, naming the storage
     */
    public static IllegalArgumentException notOnTheDesktop(KeyStorage storage) {
        return new IllegalArgumentException(
                "the desktop keeps the API keys in a file when the keychain cannot be used, so never " + storage);
    }
}
