package io.github.enadeag.carchargeplanner.engine.forecast;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ProviderTextTest {

    @Test
    void theProviderIsNamedBeforeItsWords() {
        assertThat(ProviderText.quoted("Open-Meteo", "Latitude must be in range", List.of()))
                .isEqualTo("Open-Meteo says: Latitude must be in range");
    }

    /** A direction override would turn what follows it around; a bell or a new line has no place in a message. */
    @Test
    void controlAndDirectionCharactersAreTakenOut() {
        assertThat(ProviderText.cleaned("bad‮txt.exe\u0007 key\r\n\tnow​!", List.of()))
                .isEqualTo("bad txt.exe key now !");
    }

    @Test
    void aLongTextIsCutShortWithAnEllipsis() {
        var cleaned = ProviderText.cleaned("a".repeat(5000), List.of());
        assertThat(cleaned).isEqualTo("a".repeat(ProviderText.MAX_LENGTH) + "…");
    }

    @Test
    void aCharacterOutsideTheBasicPlaneIsNeverCutInHalf() {
        var cleaned = ProviderText.cleaned("😀".repeat(ProviderText.MAX_LENGTH + 5), List.of());
        assertThat(cleaned).isEqualTo("😀".repeat(ProviderText.MAX_LENGTH) + "…");
    }

    @Test
    void aSecretIsReplacedBeforeTheTextIsCutSoNoPartOfItIsLeft() {
        var secret = "Key0123456789";
        var cleaned = ProviderText.cleaned("x".repeat(ProviderText.MAX_LENGTH - 4) + secret + " and more", List.of(secret));
        assertThat(cleaned).isEqualTo("x".repeat(ProviderText.MAX_LENGTH - 4) + "***…");
        assertThat(cleaned).doesNotContain("Key0");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "you sent ab+c/d=  | ab+c/d=  | you sent ***",
        "you sent ab%2Bc%2Fd%3D | ab+c/d= | you sent ***",
        "keys LongKey and Long | Long,LongKey | keys *** and ***",
        "nothing secret here | '' | nothing secret here",
    })
    void everySecretIsRedactedAlsoAsAWebAddressCarriesIt(String text, String secrets, String expected) {
        assertThat(ProviderText.redacted(text.strip(), List.of(secrets.split(",")))).isEqualTo(expected);
    }
}
