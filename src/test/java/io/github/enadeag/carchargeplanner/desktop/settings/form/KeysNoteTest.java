package io.github.enadeag.carchargeplanner.desktop.settings.form;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.enadeag.carchargeplanner.desktop.text.Language;
import io.github.enadeag.carchargeplanner.desktop.text.Messages;
import io.github.enadeag.carchargeplanner.desktop.text.TextKey;
import io.github.enadeag.carchargeplanner.engine.secrets.KeyStorage;
import io.github.enadeag.carchargeplanner.engine.secrets.SecretsProblem;
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
    void keysThatCouldNotBeReadAreSaidFirstWithoutTheEnginesWords() {
        assertThat(KeysNote.of(KeyStorage.KEYCHAIN, Optional.of(SecretsProblem.Kind.FAILED), FILE, ENGLISH))
                .isEqualTo("Your saved keys could not be read. Enter them again and save. " + IN_KEYCHAIN);
    }

    @Test
    void theProblemIsSaidInTheOwnersLanguage() {
        var dutch = new Messages(Language.DUTCH);

        assertThat(KeysNote.of(KeyStorage.FILE, Optional.of(SecretsProblem.Kind.FAILED), FILE, dutch))
                .isEqualTo("Je opgeslagen sleutels konden niet gelezen worden. Vul ze opnieuw in en sla op. "
                        + dutch.text(TextKey.KEYS_IN_FILE, "keys.json"));
    }

    @Test
    void aLockedKeychainIsTheReasonItselfSoNoOtherProblemIsSaid() {
        var locked = KeysNote.of(KeyStorage.LOCKED_KEYCHAIN, Optional.empty(), FILE, ENGLISH);

        assertThat(KeysNote.of(KeyStorage.LOCKED_KEYCHAIN, Optional.of(SecretsProblem.Kind.FAILED), FILE, ENGLISH))
                .isEqualTo(locked);
        assertThat(KeysNote.of(KeyStorage.LOCKED_KEYCHAIN, Optional.of(SecretsProblem.Kind.LOCKED), FILE, ENGLISH))
                .isEqualTo(locked);
    }

    @Test
    void aProblemThatSaysTheKeychainIsLockedIsSaidAsSoEvenBeforeTheStorageKnows() {
        assertThat(KeysNote.of(KeyStorage.KEYCHAIN, Optional.of(SecretsProblem.Kind.LOCKED), FILE, ENGLISH))
                .isEqualTo(KeysNote.of(KeyStorage.LOCKED_KEYCHAIN, Optional.empty(), FILE, ENGLISH));
    }
}
