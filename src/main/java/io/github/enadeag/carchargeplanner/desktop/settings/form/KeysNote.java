package io.github.enadeag.carchargeplanner.desktop.settings.form;

import io.github.enadeag.carchargeplanner.desktop.text.Messages;
import io.github.enadeag.carchargeplanner.desktop.text.TextKey;
import io.github.enadeag.carchargeplanner.engine.secrets.KeyStorage;
import java.nio.file.Path;
import java.util.Optional;

/** What the forecast page says about where the API keys are kept, and why they could not be read. */
public final class KeysNote {

    private KeysNote() {
    }

    /**
     * The note.
     *
     * @param storage where the keys are kept now
     * @param problem why the keys could not be read, in words for the owner; empty when they could
     * @param keysFile the file the keys are kept in when there is no usable keychain
     * @param messages the texts, in the owner's language
     * @return where the keys are kept, after the problem when there is one; a locked keychain is the reason itself,
     *     so the note then only says that, in the owner's language
     */
    public static String of(KeyStorage storage, Optional<String> problem, Path keysFile, Messages messages) {
        var where = switch (storage) {
            case KEYCHAIN -> messages.text(TextKey.KEYS_IN_KEYCHAIN);
            case LOCKED_KEYCHAIN -> messages.text(TextKey.KEYS_IN_LOCKED_KEYCHAIN);
            case FILE -> messages.text(TextKey.KEYS_IN_FILE, keysFile.toString());
        };
        return problem.filter(text -> storage != KeyStorage.LOCKED_KEYCHAIN)
                .map(text -> messages.text(TextKey.KEYS_PROBLEM, text) + " " + where).orElse(where);
    }
}
