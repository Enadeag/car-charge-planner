package nl.enadeag.carchargeplanner.desktop.dashboard.away;

import nl.enadeag.carchargeplanner.desktop.text.TextKey;
import nl.enadeag.carchargeplanner.engine.away.OneOffAwayPeriod;
import java.time.InstantSource;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Locale;
import java.util.Optional;

/**
 * Whether what the owner entered for an away period on one day can be saved, and as which period: the times must be
 * times, the end must be after the start and the period must not have ended yet.
 */
final class OneOffAwayCheck {

    /** Both of the app's languages write a time on a 24-hour clock; a leading zero may be left out. */
    private static final DateTimeFormatter TYPED = DateTimeFormatter.ofPattern("H:mm", Locale.ROOT)
            .withResolverStyle(ResolverStyle.STRICT);

    private final LocalDate day;
    private final ZoneId zone;
    private final InstantSource clock;

    /**
     * Makes the check for one day.
     *
     * @param day the day the car is away
     * @param zone the time zone of the owner's home, in which the times are read
     * @param clock tells the time at which a period must not have ended yet
     */
    OneOffAwayCheck(LocalDate day, ZoneId zone, InstantSource clock) {
        this.day = day;
        this.zone = zone;
        this.clock = clock;
    }

    /**
     * The whole day.
     *
     * @return the period of the day, or why the day has passed
     */
    Checked wholeDay() {
        return notPassed(OneOffAwayPeriod.allDay(day, zone));
    }

    /**
     * Part of the day.
     *
     * @param start the start as typed, such as "9:00" or "09:00"; spaces around it are ignored
     * @param end the end as typed, in the same way
     * @return the period from the start to the end, or why it cannot be saved
     */
    Checked partOfDay(String start, String end) {
        var from = time(start);
        var to = time(end);
        if (from.isEmpty() || to.isEmpty()) {
            return new Checked.Refused(TextKey.PROBLEM_NOT_A_TIME);
        }
        OneOffAwayPeriod period;
        try {
            period = OneOffAwayPeriod.on(day, from.get(), to.get(), zone);
        } catch (IllegalArgumentException _) {
            // also when the clock is put forward between the two times, so both are the same moment
            return new Checked.Refused(TextKey.ONE_OFF_END_BEFORE_START);
        }
        return notPassed(period);
    }

    private Checked notPassed(OneOffAwayPeriod period) {
        return period.end().isAfter(clock.instant()) ? new Checked.Valid(period)
                : new Checked.Refused(TextKey.ONE_OFF_PASSED);
    }

    private static Optional<LocalTime> time(String text) {
        try {
            return Optional.of(LocalTime.parse(text.strip(), TYPED));
        } catch (DateTimeParseException _) {
            return Optional.empty();
        }
    }

    /** What the entry holds: a period that can be saved, or why it cannot. */
    sealed interface Checked {

        /**
         * A period that can be saved.
         *
         * @param period the period entered
         */
        record Valid(OneOffAwayPeriod period) implements Checked {
        }

        /**
         * Why the entered period cannot be saved.
         *
         * @param why the text of the problem
         */
        record Refused(TextKey why) implements Checked {
        }
    }
}
