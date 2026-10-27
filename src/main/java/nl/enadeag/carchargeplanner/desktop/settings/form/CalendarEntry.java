package nl.enadeag.carchargeplanner.desktop.settings.form;

import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.desktop.text.TextKey;
import nl.enadeag.carchargeplanner.engine.config.CalendarSettings;
import nl.enadeag.carchargeplanner.engine.setting.InvalidSetting;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

/**
 * The calendar's settings as the Calendar page shows them: the source as typed, the travel margin in minutes and the
 * all-day times as typed.
 *
 * @param icalLink the calendar's secret iCal address as typed; empty for none
 * @param icsFile the path of the {@code .ics} file as typed; empty for none
 * @param marginMinutes the travel margin, in minutes
 * @param allDayDeparture when the car leaves for an all-day event, as typed, such as "09:00"
 * @param allDayReturn when the car is back from an all-day event, as typed, such as "18:00"
 */
public record CalendarEntry(String icalLink, String icsFile, double marginMinutes, String allDayDeparture,
        String allDayReturn) {

    /** A field of the Calendar page that can show a problem. */
    public enum Field {
        /** The iCal link. */
        ICAL_LINK,
        /** The {@code .ics} file. */
        ICS_FILE,
        /** The travel margin. */
        TRAVEL_MARGIN,
        /** When the car leaves for an all-day event. */
        ALL_DAY_DEPARTURE,
        /** When the car is back from an all-day event. */
        ALL_DAY_RETURN
    }

    /** The settings as entered, or what is wrong with them. */
    public sealed interface Checked {

        /**
         * The settings' record accepted them, and the link has the form of one.
         *
         * @param settings the calendar's settings that are not secret
         */
        record Accepted(CalendarSettings settings) implements Checked {
        }

        /**
         * A value is refused.
         *
         * @param field the field that shows the problem
         * @param problem what the field says, in the owner's language
         */
        record Refused(Field field, String problem) implements Checked {
        }
    }

    /**
     * What the page shows for the stored settings and link.
     *
     * @param settings the calendar's settings as the engine keeps them
     * @param icalLink the stored iCal link; empty for none
     * @return their values as the fields show them
     */
    public static CalendarEntry of(CalendarSettings settings, String icalLink) {
        return new CalendarEntry(icalLink, settings.icsFile().map(Path::toString).orElse(""),
                settings.travelMargin().toMinutes(), TimeText.write(settings.allDayDeparture()),
                TimeText.write(settings.allDayReturn()));
    }

    /**
     * Checks the entry: a link of the form a calendar gives, a file by its whole path, not both, and what the
     * settings' record accepts, with the margin rounded to whole minutes.
     *
     * @param messages the texts, in the owner's language
     * @return the settings, or the first problem and the field that shows it
     */
    public Checked check(Messages messages) {
        var link = icalLink.strip();
        if (!link.isEmpty() && !isCalendarLink(link)) {
            return new Checked.Refused(Field.ICAL_LINK, messages.text(TextKey.PROBLEM_CALENDAR_LINK));
        }
        var fileText = icsFile.strip();
        if (!link.isEmpty() && !fileText.isEmpty()) {
            return new Checked.Refused(Field.ICS_FILE, messages.text(TextKey.PROBLEM_BOTH_SOURCES));
        }
        Optional<Path> file = Optional.empty();
        if (!fileText.isEmpty()) {
            try {
                file = Optional.of(Path.of(fileText));
            } catch (InvalidPathException _) {
                return new Checked.Refused(Field.ICS_FILE,
                        ProblemTexts.of(InvalidSetting.Rule.NOT_A_FULL_PATH, messages));
            }
        }
        var departure = TimeText.read(allDayDeparture);
        var back = TimeText.read(allDayReturn);
        if (departure.isEmpty()) {
            return new Checked.Refused(Field.ALL_DAY_DEPARTURE, messages.text(TextKey.PROBLEM_NOT_A_TIME));
        }
        if (back.isEmpty()) {
            return new Checked.Refused(Field.ALL_DAY_RETURN, messages.text(TextKey.PROBLEM_NOT_A_TIME));
        }
        try {
            return new Checked.Accepted(new CalendarSettings(file, Duration.ofMinutes(Math.round(marginMinutes)),
                    departure.get(), back.get()));
        } catch (InvalidSetting e) {
            var field = switch (e.setting()) {
                case CALENDAR_FILE -> Field.ICS_FILE;
                case TRAVEL_MARGIN -> Field.TRAVEL_MARGIN;
                default -> Field.ALL_DAY_RETURN; // the only other setting the record refuses: before the departure
            };
            return new Checked.Refused(field, ProblemTexts.of(e.rule(), messages));
        }
    }

    /** An https or webcal address with a host, as calendars give their secret address; webcal is https underneath. */
    private static boolean isCalendarLink(String text) {
        if (text.chars().anyMatch(c -> Character.isWhitespace(c) || Character.isISOControl(c))) {
            return false;
        }
        try {
            var uri = new URI(text);
            var scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            return (scheme.equals("https") || scheme.equals("webcal")) && uri.getHost() != null;
        } catch (URISyntaxException _) {
            return false;
        }
    }
}
