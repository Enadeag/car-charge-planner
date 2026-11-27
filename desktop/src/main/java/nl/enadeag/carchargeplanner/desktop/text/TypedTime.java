package nl.enadeag.carchargeplanner.desktop.text;

import java.text.DateFormatSymbols;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Reads a time of day as the owner types it, on either clock whatever the system's: "19:30", or "7:30 pm" with a
 * morning or afternoon marker before or after the time.
 */
final class TypedTime {

    /** The hours and minutes, with the text before and after them, which may hold a marker. */
    private static final Pattern TIME = Pattern.compile("(?<before>\\D*?)\\s*(?<hours>\\d{1,2})(?::(?<minutes>\\d{2}))?"
            + "\\s*(?<after>\\D*)");
    /** Longer than any time, such as "12:30 P. M.", with room; a longer text is refused before it is matched. */
    private static final int LONGEST = 20;
    /** What is left out of a marker before it is compared, so "p.m.", "P. M." and "pm" are one. */
    private static final Pattern IGNORED_IN_A_MARKER = Pattern.compile("[.\\s]");

    private final Locale locale;
    /** Each marker as {@link #normal} writes it, and whether it is the afternoon's. */
    private final Map<String, Boolean> markers = new HashMap<>();

    /**
     * Knows the English markers and the language's own.
     *
     * @param locale the language's locale, whose markers (Dutch "a.m." and "p.m.") are read as well
     */
    TypedTime(Locale locale) {
        this.locale = locale;
        var own = DateFormatSymbols.getInstance(locale).getAmPmStrings();
        markers.put(normal(own[0]), false);
        markers.put(normal(own[1]), true);
        markers.put("am", false);
        markers.put("pm", true);
    }

    /**
     * The time a text stands for.
     *
     * @param text what the owner typed; spaces around it are ignored, and so is the case of a marker
     * @return the time; empty when the text is not hours and minutes from 0:00 to 23:59, nor hours from 1 to 12 with
     *     or without minutes and a marker
     */
    Optional<LocalTime> read(String text) {
        // a narrow or non-breaking space, as the app itself writes before a marker, is a space too
        var stripped = text.replace(' ', ' ').replace(' ', ' ').strip();
        // the pattern takes ever longer on a long text, such as a pasted paragraph with spaces in it
        if (stripped.length() > LONGEST) {
            return Optional.empty();
        }
        var matcher = TIME.matcher(stripped);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        int hours = Integer.parseInt(matcher.group("hours"));
        var minutesTyped = matcher.group("minutes");
        int minutes = minutesTyped == null ? 0 : Integer.parseInt(minutesTyped);
        if (minutes > 59) {
            return Optional.empty();
        }
        var before = normal(matcher.group("before"));
        var after = normal(matcher.group("after"));
        if (before.isEmpty() && after.isEmpty()) {
            return minutesTyped != null && hours <= 23 ? Optional.of(LocalTime.of(hours, minutes)) : Optional.empty();
        }
        var afternoon = before.isEmpty() || after.isEmpty() ? markers.get(before + after) : null;
        if (afternoon == null || hours < 1 || hours > 12) {
            return Optional.empty();
        }
        return Optional.of(LocalTime.of(hours % 12 + (afternoon ? 12 : 0), minutes));
    }

    private String normal(String marker) {
        return IGNORED_IN_A_MARKER.matcher(marker).replaceAll("").toLowerCase(locale);
    }
}
