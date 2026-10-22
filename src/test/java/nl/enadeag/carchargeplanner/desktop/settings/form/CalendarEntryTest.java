package nl.enadeag.carchargeplanner.desktop.settings.form;

import static org.assertj.core.api.Assertions.assertThat;

import nl.enadeag.carchargeplanner.desktop.text.Language;
import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.desktop.text.TextKey;
import nl.enadeag.carchargeplanner.engine.config.CalendarSettings;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CalendarEntryTest {

    private static final Messages MESSAGES = new Messages(Language.ENGLISH);
    private static final String FILE = Path.of("calendar.ics").toAbsolutePath().toString();

    private static CalendarEntry entry(String link, String file) {
        return new CalendarEntry(link, file, 15, "09:00", "18:00");
    }

    private static CalendarEntry.Checked refused(CalendarEntry.Field field, TextKey problem) {
        return new CalendarEntry.Checked.Refused(field, MESSAGES.text(problem));
    }

    @Test
    void theStoredSettingsShowAsTheyAreAndAreAcceptedAsTheyAre() {
        var stored = new CalendarSettings(Optional.of(Path.of(FILE)), Duration.ofMinutes(20), LocalTime.of(8, 30),
                LocalTime.of(19, 0));

        var shown = CalendarEntry.of(stored, "");

        assertThat(shown).isEqualTo(new CalendarEntry("", FILE, 20, "08:30", "19:00"));
        assertThat(shown.check(MESSAGES)).isEqualTo(new CalendarEntry.Checked.Accepted(stored));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "https://calendar.google.com/calendar/ical/x/private-abc/basic.ics",
        "webcal://p01-caldav.icloud.com/published/2/abc",
        "HTTPS://outlook.office365.com/owa/calendar/x/reachcalendar.ics",
        "  https://calendar.example/secret.ics  ",
    })
    void aCalendarsSecretAddressIsAccepted(String link) {
        assertThat(entry(link, "").check(MESSAGES))
                .isEqualTo(new CalendarEntry.Checked.Accepted(CalendarSettings.STANDARD));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "http://calendar.example/secret.ics",
        "calendar.example/secret.ics",
        "https:///secret.ics",
        "https://calendar.example/my calendar.ics",
        "https://calendar.example/\u0007",
        "https://calendar.example/%zz",
        "ftp://calendar.example/secret.ics",
    })
    void anotherAddressIsRefused(String link) {
        assertThat(entry(link, "").check(MESSAGES))
                .isEqualTo(refused(CalendarEntry.Field.ICAL_LINK, TextKey.PROBLEM_CALENDAR_LINK));
    }

    @Test
    void anAddressAndAFileTogetherAreRefusedAtTheFile() {
        assertThat(entry("https://calendar.example/secret.ics", FILE).check(MESSAGES))
                .isEqualTo(refused(CalendarEntry.Field.ICS_FILE, TextKey.PROBLEM_BOTH_SOURCES));
    }

    @Test
    void aFileIsTakenByItsWholePath() {
        assertThat(entry("", " " + FILE + " ").check(MESSAGES)).isEqualTo(new CalendarEntry.Checked.Accepted(
                new CalendarSettings(Optional.of(Path.of(FILE)), Duration.ofMinutes(15), LocalTime.of(9, 0),
                        LocalTime.of(18, 0))));
        assertThat(entry("", "calendar.ics").check(MESSAGES))
                .isEqualTo(refused(CalendarEntry.Field.ICS_FILE, TextKey.PROBLEM_NOT_A_FULL_PATH));
        assertThat(entry("", "\u0000calendar.ics").check(MESSAGES))
                .isEqualTo(refused(CalendarEntry.Field.ICS_FILE, TextKey.PROBLEM_NOT_A_FULL_PATH));
    }

    @Test
    void theAllDayTimesMustBeTimesAndTheReturnAfterTheDeparture() {
        assertThat(new CalendarEntry("", "", 15, "9am", "18:00").check(MESSAGES))
                .isEqualTo(refused(CalendarEntry.Field.ALL_DAY_DEPARTURE, TextKey.PROBLEM_NOT_A_TIME));
        assertThat(new CalendarEntry("", "", 15, "09:00", "").check(MESSAGES))
                .isEqualTo(refused(CalendarEntry.Field.ALL_DAY_RETURN, TextKey.PROBLEM_NOT_A_TIME));
        assertThat(new CalendarEntry("", "", 15, "18:00", "09:00").check(MESSAGES))
                .isEqualTo(refused(CalendarEntry.Field.ALL_DAY_RETURN, TextKey.PROBLEM_NOT_AFTER_DEPARTURE));
    }

    @Test
    void theMarginIsRoundedToWholeMinutesAndMayBeAtMostFourHours() {
        assertThat(new CalendarEntry("", "", 20.4, "09:00", "18:00").check(MESSAGES))
                .isEqualTo(new CalendarEntry.Checked.Accepted(new CalendarSettings(Optional.empty(),
                        Duration.ofMinutes(20), LocalTime.of(9, 0), LocalTime.of(18, 0))));
        assertThat(new CalendarEntry("", "", 241, "09:00", "18:00").check(MESSAGES))
                .isEqualTo(refused(CalendarEntry.Field.TRAVEL_MARGIN, TextKey.PROBLEM_OUT_OF_RANGE));
    }
}
