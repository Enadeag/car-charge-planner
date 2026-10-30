package nl.enadeag.carchargeplanner.host;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class OsTest {

    @ParameterizedTest
    @CsvSource({
        "Linux, LINUX",
        "Windows 11, WINDOWS",
        "Windows Server 2025, WINDOWS",
        "Mac OS X, OTHER",
        "FreeBSD, OTHER"})
    void theSystemIsKnownByTheStartOfItsName(String osName, Os expected) {
        assertThat(Os.of(osName)).isEqualTo(expected);
    }
}
