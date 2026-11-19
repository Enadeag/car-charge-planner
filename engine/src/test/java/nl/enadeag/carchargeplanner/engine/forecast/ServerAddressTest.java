package nl.enadeag.carchargeplanner.engine.forecast;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class ServerAddressTest {

    @ParameterizedTest
    @CsvSource({
        "https://customer-api.open-meteo.com/, https://customer-api.open-meteo.com",
        "'  https://example.org/meteo//  ', https://example.org/meteo",
        "HTTPS://Example.org:8443, HTTPS://Example.org:8443",
        "http://localhost:8080, http://localhost:8080",
        "http://LocalHost, http://LocalHost",
        "HTTP://localhost, HTTP://localhost",
        "http://127.0.0.1:8080/om, http://127.0.0.1:8080/om",
        "http://[::1]:8080, http://[::1]:8080",
    })
    void httpsAnywhereOrPlainHttpOnThisComputerIsAcceptedWhereThatIsAllowed(String typed, String address) {
        assertThat(ServerAddress.parse(typed, PlainHttp.TO_THIS_COMPUTER)).map(ServerAddress::uri)
                .contains(URI.create(address));
        assertThat(SettingFormat.SERVER_ADDRESS.accepts(typed, PlainHttp.TO_THIS_COMPUTER)).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
        "https://customer-api.open-meteo.com/, https://customer-api.open-meteo.com",
        "'  https://example.org/meteo//  ', https://example.org/meteo",
        "HTTPS://Example.org:8443, HTTPS://Example.org:8443",
        "https://localhost:8443, https://localhost:8443",
    })
    void httpsIsAcceptedWherePlainHttpIsNever(String typed, String address) {
        assertThat(ServerAddress.parse(typed, PlainHttp.NEVER)).map(ServerAddress::uri).contains(URI.create(address));
        assertThat(SettingFormat.SERVER_ADDRESS.accepts(typed, PlainHttp.NEVER)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://localhost", "http://localhost:8080", "http://LocalHost", "HTTP://localhost",
        "http://127.0.0.1:8080/om", "http://[::1]:8080"})
    void plainHttpToThisComputerIsRefusedWherePlainHttpIsNever(String typed) {
        assertThat(ServerAddress.parse(typed, PlainHttp.NEVER)).isEmpty();
        assertThat(SettingFormat.SERVER_ADDRESS.accepts(typed, PlainHttp.NEVER)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(PlainHttp.class)
    void aBlankServerAddressIsLeftToTheCheckOnRequiredSettings(PlainHttp plainHttp) {
        assertThat(SettingFormat.SERVER_ADDRESS.accepts("", plainHttp)).isTrue();
        assertThat(SettingFormat.SERVER_ADDRESS.accepts("  ", plainHttp)).isTrue();
        assertThat(ServerAddress.parse("  ", plainHttp)).isEmpty();
    }

    @Test
    void readingAnAddressWithoutSayingWherePlainHttpIsAllowedIsAMistake() {
        assertThatThrownBy(() -> ServerAddress.parse("https://example.org", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theRecordItselfAllowsPlainHttpToThisComputer() {
        assertThat(new ServerAddress(URI.create("http://localhost:8080")).withPath("/v1"))
                .isEqualTo("http://localhost:8080/v1");
    }

    /** Each looks like this computer, or like the server, to a check on how the address starts. */
    @ParameterizedTest
    @ValueSource(strings = {
        "http://localhost.evil.example", "http://localhost@evil.example", "http://127.0.0.1.nip.io",
        "http://127.0.0.1@evil.example:80", "http://[::1].evil.example", "http://[::2]", "http://0.0.0.0",
        "http://localhost.", "http://[0:0:0:0:0:0:0:1]",
        "http://example.org", "https://user@example.org", "https://user:pw@example.org", "https://example.org/?key=1",
        "https://example.org?", "https://example.org#top", "https://", "https:example.org", "ftp://example.org",
        "example.org", "javascript:alert(1)", "https://exa mple.org", "file:///etc/passwd", ""})
    void anyOtherAddressIsRefused(String typed) {
        for (var plainHttp : PlainHttp.values()) {
            assertThat(ServerAddress.parse(typed, plainHttp)).as("%s", plainHttp).isEmpty();
            assertThat(SettingFormat.SERVER_ADDRESS.accepts(typed, plainHttp)).as("%s", plainHttp)
                    .isEqualTo(typed.isBlank());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://example.org", "https://example.org/", "https://u@example.org"})
    void anAddressThatIsNotAllowedCannotBeMade(String uri) {
        assertThatThrownBy(() -> new ServerAddress(URI.create(uri))).isInstanceOf(IllegalArgumentException.class);
    }
}
