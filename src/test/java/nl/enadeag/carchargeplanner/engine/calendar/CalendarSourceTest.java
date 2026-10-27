package nl.enadeag.carchargeplanner.engine.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class CalendarSourceTest {

    @ParameterizedTest
    @CsvSource({
        "https://calendar.example/private-abc/basic.ics, https://calendar.example/private-abc/basic.ics",
        "webcal://calendar.example/private-abc/basic.ics, https://calendar.example/private-abc/basic.ics",
        "WEBCAL://calendar.example/feed?token=abc, https://calendar.example/feed?token=abc",
        "'  https://calendar.example/basic.ics  ', https://calendar.example/basic.ics"
    })
    void anIcalLinkIsFetchedOverHttps(String typed, String fetched) {
        assertThat(CalendarSource.IcalLink.parse(typed).map(CalendarSource.IcalLink::address))
                .contains(URI.create(fetched));
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://calendar.example/basic.ics", "ftp://calendar.example/basic.ics",
        "calendar.example/basic.ics", "https:///basic.ics", "https://calendar.example/my calendar.ics",
        "mailto:someone@calendar.example", ""})
    void anAddressThatIsNotHttpsOrWebcalIsNoIcalLink(String typed) {
        assertThat(CalendarSource.IcalLink.parse(typed)).isEqualTo(Optional.empty());
    }

    @Test
    void anIcalLinkNeverShowsItsAddressInText() {
        var link = CalendarSource.IcalLink.parse("https://calendar.example/private-abc/basic.ics").orElseThrow();

        assertThat(link.toString()).startsWith("IcalLink").doesNotContain("calendar.example")
                .doesNotContain("private-abc");
    }

    @Test
    void anIcalLinkRefusesAnAddressThatIsNotHttpsWithoutNamingIt() {
        assertThatThrownBy(() -> new CalendarSource.IcalLink(URI.create("http://calendar.example/private-abc")))
                .isInstanceOf(IllegalArgumentException.class).message().doesNotContain("private-abc");
    }

    @Test
    void anIcsFileNeedsAWholePath() {
        assertThatThrownBy(() -> new CalendarSource.IcsFile(Path.of("calendar.ics")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
