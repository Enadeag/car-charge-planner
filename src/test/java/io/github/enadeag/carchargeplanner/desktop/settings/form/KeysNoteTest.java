package io.github.enadeag.carchargeplanner.desktop.settings.form;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.enadeag.carchargeplanner.desktop.text.Language;
import io.github.enadeag.carchargeplanner.desktop.text.Messages;
import io.github.enadeag.carchargeplanner.engine.secrets.KeyStorage;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class KeysNoteTest {

    private static final Messages ENGLISH = new Messages(Language.ENGLISH);
    private static final Path FILE = Path.of("keys.json");
    private static final String IN_KEYCHAIN = "API keys are kept in the system keychain, not in the settings files.";

    @Test
    void theNoteSaysWhereTheKeysAreKept() {
        assertThat(KeysNote.of(KeyStorage.KEYCHAIN, Optional.empty(), FILE, ENGLISH)).isEqualTo(IN_KEYCHAIN);
        assertThat(KeysNote.of(KeyStorage.FILE, Optional.empty(), FILE, ENGLISH)).isEqualTo(
                "No system keychain is available, so API keys are kept in keys.json, which only you can read.");
        assertThat(KeysNote.of(KeyStorage.LOCKED_KEYCHAIN, Optional.empty(), FILE, ENGLISH))
                .startsWith("API keys are kept in the system keychain, which is locked");
    }

    @Test
    void keysThatCouldNotBeReadAreSaidFirst() {
        assertThat(KeysNote.of(KeyStorage.KEYCHAIN, Optional.of("the keychain did not answer"), FILE, ENGLISH))
                .isEqualTo("Your saved keys could not be read: the keychain did not answer " + IN_KEYCHAIN);
    }

    @Test
    void aLockedKeychainIsTheReasonItselfSoItsWordsAreLeftOut() {
        assertThat(KeysNote.of(KeyStorage.LOCKED_KEYCHAIN, Optional.of("Locked."), FILE, ENGLISH))
                .isEqualTo(KeysNote.of(KeyStorage.LOCKED_KEYCHAIN, Optional.empty(), FILE, ENGLISH));
    }
}
