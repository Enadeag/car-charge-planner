package io.github.enadeag.carchargeplanner.desktop;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** How the dashboard writes times, numbers and days: English, with a decimal point, until the app is translated. */
final class Format {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter WEEKDAY = DateTimeFormatter.ofPattern("EEEE", Locale.ENGLISH);
    private static final DateTimeFormatter WEEKDAY_SHORT = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH);

    private Format() {
    }

    /**
     * Fills in a {@link String#format} pattern with English numbers.
     *
     * @param pattern the pattern, such as "Set %d A (%.1f kW)"
     * @param values the values for its placeholders
     * @return the text, with a decimal point whatever the system's language
     */
    static String text(String pattern, Object... values) {
        return String.format(Locale.ENGLISH, pattern, values);
    }

    /**
     * A moment as a clock time.
     *
     * @param moment the moment
     * @param zone the time zone of the owner's home, in which the time is told
     * @return hours and minutes on a 24-hour clock, such as "09:05"
     */
    static String time(Instant moment, ZoneId zone) {
        return TIME.format(moment.atZone(zone));
    }

    /**
     * A day's short name.
     *
     * @param day the day to name
     * @param today the day the plan was made, in the owner's home
     * @return "Today", or the weekday's short name such as "Fri"
     */
    static String tab(LocalDate day, LocalDate today) {
        return day.equals(today) ? "Today" : WEEKDAY_SHORT.format(day);
    }

    /**
     * A day's name.
     *
     * @param day the day to name
     * @param today the day the plan was made, in the owner's home
     * @return "Today", "Tomorrow", or the weekday such as "Sunday"
     */
    static String title(LocalDate day, LocalDate today) {
        if (day.equals(today)) {
            return "Today";
        }
        if (day.equals(today.plusDays(1))) {
            return "Tomorrow";
        }
        return WEEKDAY.format(day);
    }

    /**
     * A day's name as it goes in the middle of a sentence.
     *
     * @param day the day to name
     * @param today the day the plan was made, in the owner's home
     * @return "today", "tomorrow", or the weekday such as "Sunday"
     */
    static String inSentence(LocalDate day, LocalDate today) {
        var title = title(day, today);
        return day.isAfter(today.plusDays(1)) ? title : title.toLowerCase(Locale.ENGLISH);
    }
}
