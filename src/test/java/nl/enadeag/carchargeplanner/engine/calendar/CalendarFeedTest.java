package nl.enadeag.carchargeplanner.engine.calendar;

import static nl.enadeag.carchargeplanner.engine.calendar.RecordedCalendars.GOOGLE;
import static nl.enadeag.carchargeplanner.engine.calendar.RecordedCalendars.NEXTCLOUD;
import static nl.enadeag.carchargeplanner.engine.calendar.RecordedCalendars.OCTOBER;
import static nl.enadeag.carchargeplanner.engine.calendar.RecordedCalendars.text;
import static org.assertj.core.api.Assertions.assertThat;

import nl.enadeag.carchargeplanner.LogCapture;
import nl.enadeag.carchargeplanner.engine.config.CalendarSettings;
import nl.enadeag.carchargeplanner.engine.forecast.HttpGet;
import nl.enadeag.carchargeplanner.engine.forecast.ReplayHttp;
import java.io.IOException;
import java.net.ConnectException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CalendarFeedTest {

    private static final Instant NOW = Instant.parse("2026-10-22T08:00:00Z");
    private static final Consents BOTH = new Consents(Map.of(
            Consent.READ_CALENDAR, ConsentRecord.givenNow(Consent.READ_CALENDAR, NOW.minusSeconds(3600)),
            Consent.SEND_LOCATIONS, ConsentRecord.givenNow(Consent.SEND_LOCATIONS, NOW.minusSeconds(3600))));
    private static final Consents ONLY_READING = new Consents(Map.of(
            Consent.READ_CALENDAR, ConsentRecord.givenNow(Consent.READ_CALENDAR, NOW.minusSeconds(3600))));
    private static final CalendarSettings NO_FILE = CalendarSettings.STANDARD;
    private static final String ADDRESS = "https://calendar.example/private-abc/basic.ics";

    @TempDir
    Path folder;

    private ReplayHttp http = ReplayHttp.always(new HttpGet.Reply(200, text(GOOGLE)));
    private final List<String> lookups = new ArrayList<>();

    private CalendarFeed feed() {
        return new CalendarFeed(new CalendarFetcher(http), new IcsReader());
    }

    /** The stored iCal address; each lookup is noted, as it asks the keychain. */
    private Optional<CalendarSource.IcalLink> link() {
        lookups.add(ADDRESS);
        return CalendarSource.IcalLink.parse(ADDRESS);
    }

    private Optional<CalendarSource.IcalLink> none() {
        lookups.add("none");
        return Optional.empty();
    }

    private CalendarRead refresh(CalendarFeed feed, Instant at) {
        return feed.refresh(BOTH, NO_FILE, this::link, OCTOBER, at);
    }

    private static CalendarSettings withFile(Path file) {
        return new CalendarSettings(Optional.of(file), Duration.ofMinutes(15), LocalTime.of(9, 0),
                LocalTime.of(18, 0));
    }

    private Path icsFile(String calendar) throws IOException {
        return Files.writeString(folder.resolve("calendar.ics"), text(calendar)).toAbsolutePath();
    }

    @Test
    void theCalendarIsReadWithANewPlanAndItsEventsKept() {
        var feed = feed();

        var read = refresh(feed, NOW);

        assertThat(read).isEqualTo(new CalendarRead.Read(NOW, 7, 0));
        assertThat(feed.events()).hasSize(7);
        assertThat(feed.last()).isEqualTo(read);
    }

    @Test
    void itIsReadAgainOnlyOnceFifteenMinutesHavePassed() {
        var feed = feed();
        refresh(feed, NOW);

        refresh(feed, NOW.plus(Duration.ofMinutes(14)));
        assertThat(http.requests).hasSize(1);
        assertThat(lookups).hasSize(1);

        var again = refresh(feed, NOW.plus(CalendarFeed.EVERY));
        assertThat(http.requests).hasSize(2);
        assertThat(again).isEqualTo(new CalendarRead.Read(NOW.plus(CalendarFeed.EVERY), 7, 0));
    }

    @Test
    void itIsReadAgainAtOnceWhenTheDaysChange() {
        var feed = feed();
        refresh(feed, NOW);
        var nextDay = new CoveredDays(LocalDate.of(2026, 10, 13), OCTOBER.last(), OCTOBER.zone());

        feed.refresh(BOTH, NO_FILE, this::link, nextDay, NOW.plusSeconds(60));

        assertThat(http.requests).hasSize(2);
    }

    @Test
    void itIsReadAgainAtOnceWhenAsked() {
        var feed = feed();
        refresh(feed, NOW);

        feed.readSoon();
        refresh(feed, NOW.plusSeconds(60));

        assertThat(http.requests).hasSize(2);
    }

    @Test
    void readingNowReadsWheneverItWasReadLast() {
        var feed = feed();
        refresh(feed, NOW);

        var read = feed.readNow(BOTH, NO_FILE, this::link, OCTOBER, NOW.plusSeconds(60));

        assertThat(read).isEqualTo(new CalendarRead.Read(NOW.plusSeconds(60), 7, 0));
        assertThat(http.requests).hasSize(2);
    }

    @Test
    void aFileIsReadAgainAtOnceWhenItChanges() throws IOException {
        var file = icsFile(GOOGLE);
        var feed = feed();
        feed.refresh(BOTH, withFile(file), this::link, OCTOBER, NOW);
        feed.refresh(BOTH, withFile(file), this::link, OCTOBER, NOW.plusSeconds(60));
        assertThat(feed.events()).hasSize(7);

        Files.writeString(file, text(NEXTCLOUD));
        Files.setLastModifiedTime(file, FileTime.from(NOW.plusSeconds(90)));
        var read = feed.refresh(BOTH, withFile(file), this::link, OCTOBER, NOW.plusSeconds(120));

        assertThat(read).isEqualTo(new CalendarRead.Read(NOW.plusSeconds(120), 7, 0));
        assertThat(feed.events()).extracting(CalendarEvent::title).contains("Swimming");
        assertThat(lookups).isEmpty();
    }

    @Test
    void nothingIsReadAndNothingKeptWithoutBothConsents() {
        var feed = feed();
        refresh(feed, NOW);

        var read = feed.refresh(ONLY_READING, NO_FILE, this::link, OCTOBER, NOW.plusSeconds(60));

        assertThat(read).isEqualTo(new CalendarRead.NotRead(CalendarRead.Reason.NO_CONSENT));
        assertThat(feed.events()).isEmpty();
        assertThat(feed.readNow(ONLY_READING, NO_FILE, this::link, OCTOBER, NOW.plusSeconds(60)))
                .isEqualTo(new CalendarRead.NotRead(CalendarRead.Reason.NO_CONSENT));
        assertThat(feed.withoutPlan(ONLY_READING)).isEqualTo(new CalendarRead.NotRead(CalendarRead.Reason.NO_CONSENT));
        assertThat(http.requests).hasSize(1);
    }

    @Test
    void withoutAPlanTheEventsAreKeptWhileBothConsentsCount() {
        var feed = feed();
        refresh(feed, NOW);

        assertThat(feed.withoutPlan(BOTH)).isEqualTo(new CalendarRead.NotRead(CalendarRead.Reason.NO_PLAN));
        assertThat(feed.events()).hasSize(7);
    }

    @Test
    void withoutASourceNothingIsKept() {
        var feed = feed();
        refresh(feed, NOW);

        feed.readSoon();
        var read = feed.refresh(BOTH, NO_FILE, this::none, OCTOBER, NOW.plusSeconds(60));

        assertThat(read).isEqualTo(new CalendarRead.NotRead(CalendarRead.Reason.NO_SOURCE));
        assertThat(feed.events()).isEmpty();
    }

    @Test
    void aFailureKeepsTheEventsOfTheSameSourceAndDropsThoseOfAnother() throws IOException {
        var file = icsFile(GOOGLE);
        var feed = feed();
        feed.refresh(BOTH, withFile(file), this::link, OCTOBER, NOW);

        Files.writeString(file, "not a calendar");
        Files.setLastModifiedTime(file, FileTime.from(NOW.plusSeconds(30)));
        var failed = feed.refresh(BOTH, withFile(file), this::link, OCTOBER, NOW.plusSeconds(60));
        assertThat(failed).isInstanceOf(CalendarRead.Failed.class);
        assertThat(feed.events()).hasSize(7);

        var other = new CalendarSettings(Optional.of(folder.resolve("other.ics").toAbsolutePath()),
                Duration.ofMinutes(15), LocalTime.of(9, 0), LocalTime.of(18, 0));
        feed.readNow(BOTH, other, this::link, OCTOBER, NOW.plusSeconds(90));
        assertThat(feed.events()).isEmpty();
    }

    @Test
    void aCalendarThatStopsBeingReadIsLoggedOnceWithoutItsAddress() {
        try (var log = LogCapture.of(CalendarFeed.class)) {
            refresh(feed(), NOW);
            var broken = new CalendarFeed(new CalendarFetcher(new ReplayHttp(List.of(
                    ReplayHttp.Route.failure(new ConnectException(ADDRESS))))), new IcsReader());
            var failed = broken.readNow(BOTH, NO_FILE, this::link, OCTOBER, NOW);
            broken.readNow(BOTH, NO_FILE, this::link, OCTOBER, NOW.plusSeconds(60));

            assertThat(failed).isEqualTo(new CalendarRead.Failed(NOW, CalendarProblem.Kind.UNREACHABLE,
                    "could not reach the calendar's server (ConnectException)"));
            assertThat(log.lines()).containsExactly("INFO the calendar is read",
                    "WARNING the calendar could not be read: could not reach the calendar's server (ConnectException)");
        }
    }

    @Test
    void aCalendarThatCanBeReadAgainIsLoggedOnce() throws IOException {
        var file = icsFile(GOOGLE);
        try (var log = LogCapture.of(CalendarFeed.class)) {
            var feed = feed();
            Files.writeString(file, "not a calendar");
            feed.readNow(BOTH, withFile(file), this::link, OCTOBER, NOW);
            Files.writeString(file, text(GOOGLE));
            feed.readNow(BOTH, withFile(file), this::link, OCTOBER, NOW.plusSeconds(60));
            feed.readNow(BOTH, withFile(file), this::link, OCTOBER, NOW.plusSeconds(120));

            assertThat(log.lines()).containsExactly(
                    "WARNING the calendar could not be read: not a calendar in the iCalendar format (line 1)",
                    "INFO the calendar is read again");
        }
    }
}
