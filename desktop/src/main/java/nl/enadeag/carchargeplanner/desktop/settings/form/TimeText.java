package nl.enadeag.carchargeplanner.desktop.settings.form;

import nl.enadeag.carchargeplanner.desktop.text.Messages;
import java.time.LocalTime;
import java.util.Optional;

/**
 * A time of day as hours and minutes in a field of Settings or set-up, such as the charging hours and the weekly away
 * periods. The field shows it on the system's 12- or 24-hour clock, as {@link Messages#time(LocalTime)} writes it, and
 * reads either clock whatever the system's, as {@link Messages#readTime(String)} does: "19:30", "7:30 pm" and "7:30
 * p.m." are the same time, and "7:30" without a marker is 07:30. The settings file keeps it as {@code HH:mm} on a
 * 24-hour clock, whichever clock the field shows.
 */
public final class TimeText {

    private TimeText() {
    }

    /**
     * A time as a field shows it.
     *
     * @param time the time, to the minute
     * @param messages the owner's language and the system's clock
     * @return such as "07:30" on a 24-hour clock and "7:30 am" on a 12-hour one
     */
    public static String write(LocalTime time, Messages messages) {
        return messages.time(time);
    }

    /**
     * The time a text stands for.
     *
     * @param text what the owner typed; spaces around it are ignored
     * @param messages the owner's language, whose own morning and afternoon markers are read too
     * @return the time, or empty when the text is not a time such as 07:30 or 7:30 pm
     */
    public static Optional<LocalTime> read(String text, Messages messages) {
        return messages.readTime(text);
    }
}
