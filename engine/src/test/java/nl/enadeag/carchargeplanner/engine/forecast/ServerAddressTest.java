package nl.enadeag.carchargeplanner.engine.forecast;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class ServerAddressTest {

    @ParameterizedTest
    @CsvSource({
        "https://customer-api.open-meteo.com/, https://customer-api.open-meteo.com",
        "'  https://example.org/meteo//  ', https://example.org/meteo",
        "HTTPS://Example.org:8443, HTTPS://Example.org:8443",
        "http://localhost:8080, http://localhost:8080",
        "http://LocalHost, http://LocalHost",
        "http://127.0.0.1:8080/om, http://127.0.0.1:8080/om",
        "http://[::1]:8080, http://[::1]:8080",
    })
    void httpsAnywhereOrPlainHttpOnThisComputerIsAccepted(String typed, String address) {
        assertThat(ServerAddress.parse(typed)).map(ServerAddress::uri).contains(URI.create(address));
    }

    /** Each looks like this computer, or like the server, to a check on how the address starts. */
    @ParameterizedTest
    @ValueSource(strings = {
        "http://localhost.evil.example", "http://localhost@evil.example", "http://127.0.0.1.nip.io",
        "http://127.0.0.1@evil.example:80", "http://[::1].evil.example", "http://[::2]", "http://0.0.0.0",
        "http://example.org", "https://user@example.org", "https://user:pw@example.org", "https://example.org/?key=1",
        "https://example.org?", "https://example.org#top", "https://", "https:example.org", "ftp://example.org",
        "example.org", "javascript:alert(1)", "https://exa mple.org", "file:///etc/passwd", ""})
    void anyOtherAddressIsRefused(String typed) {
        assertThat(ServerAddress.parse(typed)).isEmpty();
        assertThat(SettingFormat.SERVER_ADDRESS.accepts(typed)).isEqualTo(typed.isBlank());
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://example.org", "https://example.org/", "https://u@example.org"})
    void anAddressThatIsNotAllowedCannotBeMade(String uri) {
        assertThatThrownBy(() -> new ServerAddress(URI.create(uri))).isInstanceOf(IllegalArgumentException.class);
    }
}
