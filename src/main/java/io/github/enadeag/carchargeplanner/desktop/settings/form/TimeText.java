package io.github.enadeag.carchargeplanner.desktop.settings.form;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Locale;
import java.util.Optional;

/** A time of day as hours and minutes, such as 07:30. Both of the app's languages write it on a 24-hour clock. */
public final class TimeText {

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)
            .withResolverStyle(ResolverStyle.STRICT);

    private TimeText() {
    }

    /**
     * A time as a field shows it.
     *
     * @param time the time, to the minute
     * @return such as "07:30"
     */
    public static String write(LocalTime time) {
        return CLOCK.format(time);
    }

    /**
     * The time a text stands for.
     *
     * @param text what the owner typed; spaces around it are ignored
     * @return the time, or empty when the text is not a time such as 07:30
     */
    public static Optional<LocalTime> read(String text) {
        try {
            return Optional.of(LocalTime.parse(text.strip(), CLOCK));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }
}
