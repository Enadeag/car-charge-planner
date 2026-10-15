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

    static String text(String pattern, Object... values) {
        return String.format(Locale.ENGLISH, pattern, values);
    }

    static String time(Instant moment, ZoneId zone) {
        return TIME.format(moment.atZone(zone));
    }

    static String tab(LocalDate day, LocalDate today) {
        return day.equals(today) ? "Today" : WEEKDAY_SHORT.format(day);
    }

    static String title(LocalDate day, LocalDate today) {
        if (day.equals(today)) {
            return "Today";
        }
        if (day.equals(today.plusDays(1))) {
            return "Tomorrow";
        }
        return WEEKDAY.format(day);
    }
}
