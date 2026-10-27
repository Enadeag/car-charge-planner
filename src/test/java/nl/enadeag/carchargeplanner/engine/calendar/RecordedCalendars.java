package nl.enadeag.carchargeplanner.engine.calendar;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;

/** The calendars in the shape Google, Outlook.com and Nextcloud export them, in the test resources beside this. */
public final class RecordedCalendars {

    public static final String GOOGLE = "google_export.ics";
    public static final String OUTLOOK = "outlook_export.ics";
    public static final String NEXTCLOUD = "nextcloud_export.ics";

    public static final ZoneId AMSTERDAM = ZoneId.of("Europe/Amsterdam");
    /** The days around the clock change of 25 October 2026 that the calendars' events fall on. */
    public static final CoveredDays OCTOBER = new CoveredDays(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 11, 10),
            AMSTERDAM);

    private RecordedCalendars() {
    }

    public static String text(String name) {
        try (var in = RecordedCalendars.class.getResourceAsStream(name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
