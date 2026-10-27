package nl.enadeag.carchargeplanner.engine.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import nl.enadeag.carchargeplanner.engine.forecast.HttpGet;
import nl.enadeag.carchargeplanner.engine.forecast.ReplayHttp;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.ConnectException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CalendarFetcherTest {

    private static final CalendarSource.IcalLink LINK =
            CalendarSource.IcalLink.parse("webcal://calendar.example/private-abc/basic.ics").orElseThrow();
    private static final String SECRET = "private-abc";

    @TempDir
    Path folder;

    private static CalendarProblem problem(ReplayHttp http, CalendarSource source) {
        var thrown = catchThrowableOfType(CalendarProblem.class,
                () -> new CalendarFetcher(http).fetch(source));
        assertThat(thrown).isNotNull();
        assertThat(thrown.getMessage()).doesNotContain(SECRET);
        return thrown;
    }

    @Test
    void anIcalLinkIsAskedOverHttpsWithATimeoutAndALimit() throws CalendarProblem {
        var http = ReplayHttp.always(new HttpGet.Reply(200, "BEGIN:VCALENDAR"));

        var text = new CalendarFetcher(http).fetch(LINK);

        assertThat(text).isEqualTo("BEGIN:VCALENDAR");
        assertThat(http.requests).containsExactly(new HttpGet.Request(
                URI.create("https://calendar.example/private-abc/basic.ics"), Map.of("Accept", "text/calendar"),
                CalendarFetcher.TIMEOUT, CalendarFetcher.MAX_SIZE));
    }

    @Test
    void aServerThatDoesNotSendTheCalendarIsNamedByItsStatusNotItsAddress() {
        var problem = problem(ReplayHttp.always(new HttpGet.Reply(404, "Not found")), LINK);

        assertThat(problem.kind()).isEqualTo(CalendarProblem.Kind.REFUSED);
        assertThat(problem.getMessage()).contains("404");
    }

    @Test
    void aServerThatCannotBeReachedIsNotNamedByItsAddress() {
        var http = new ReplayHttp(List.of(ReplayHttp.Route.failure(
                new ConnectException("could not connect to https://calendar.example/private-abc/basic.ics"))));

        assertThat(problem(http, LINK).kind()).isEqualTo(CalendarProblem.Kind.UNREACHABLE);
    }

    @Test
    void aCalendarLargerThanTheLimitIsRefused() {
        var http = new ReplayHttp(List.of(ReplayHttp.Route.failure(new HttpGet.TooLarge(CalendarFetcher.MAX_SIZE))));

        assertThat(problem(http, LINK).kind()).isEqualTo(CalendarProblem.Kind.TOO_LARGE);
    }

    @Test
    void anIcsFileIsReadAsUtf8() throws IOException, CalendarProblem {
        var file = Files.writeString(folder.resolve("calendar.ics"), "SUMMARY:Café");

        assertThat(new CalendarFetcher(new ReplayHttp(List.of())).fetch(new CalendarSource.IcsFile(file)))
                .isEqualTo("SUMMARY:Café");
    }

    @Test
    void aMissingFileCannotBeRead() {
        var file = new CalendarSource.IcsFile(folder.resolve("gone.ics"));

        assertThat(problem(new ReplayHttp(List.of()), file).kind()).isEqualTo(CalendarProblem.Kind.FILE_UNREADABLE);
    }

    @Test
    void aFileLargerThanTheLimitIsNotRead() throws IOException {
        var path = folder.resolve("huge.ics");
        try (var file = new RandomAccessFile(path.toFile(), "rw")) {
            file.setLength(CalendarFetcher.MAX_SIZE.bytes() + 1);
        }

        assertThat(problem(new ReplayHttp(List.of()), new CalendarSource.IcsFile(path)).kind())
                .isEqualTo(CalendarProblem.Kind.TOO_LARGE);
    }

    @Test
    void aFileThatIsNotUtf8IsNotACalendar() throws IOException {
        var path = Files.write(folder.resolve("latin1.ics"), new byte[] {'C', 'a', 'f', (byte) 0xE9});

        assertThat(problem(new ReplayHttp(List.of()), new CalendarSource.IcsFile(path)).kind())
                .isEqualTo(CalendarProblem.Kind.NOT_A_CALENDAR);
    }
}
