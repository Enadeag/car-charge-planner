package io.github.enadeag.carchargeplanner.desktop;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LanguageTest {

    private static final Locale DUTCH_SYSTEM = Locale.of("nl", "BE");

    @Test
    void theSystemsLanguageIsUsedWhenTheAppHasIt() {
        assertThat(Language.ofSystem(Map.of(), DUTCH_SYSTEM)).isEqualTo(Language.DUTCH);
        assertThat(Language.ofSystem(Map.of(), Locale.US)).isEqualTo(Language.ENGLISH);
    }

    @Test
    void aSystemLanguageTheAppDoesNotHaveGivesEnglish() {
        assertThat(Language.ofSystem(Map.of(), Locale.GERMANY)).isEqualTo(Language.ENGLISH);
    }

    @Test
    void theFirstLanguageTheAppHasFromTheLanguageListComesBeforeTheLocale() {
        var environment = Map.of("LANGUAGE", "de_DE:nl_NL.UTF-8:en", "LANG", "en_US.UTF-8");

        assertThat(Language.ofSystem(environment, Locale.US)).isEqualTo(Language.DUTCH);
    }

    @Test
    void thePlainCLocaleMeansEnglishWhateverTheLanguageList() {
        var environment = Map.of("LANGUAGE", "nl", "LC_ALL", "C.UTF-8");

        assertThat(Language.ofSystem(environment, DUTCH_SYSTEM)).isEqualTo(Language.ENGLISH);
    }

    @Test
    void aCodeIsFoundInAnyCaseAndAnUnknownOneIsNot() {
        assertThat(Language.ofCode("NL")).contains(Language.DUTCH);
        assertThat(Language.ofCode("de")).isEmpty();
    }
}
