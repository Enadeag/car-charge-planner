package io.github.enadeag.carchargeplanner.engine.away;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * An away period that comes back every week, such as Thursday 06:00 to 19:00, in the home's local time. It stays
 * within its day: a period over midnight is two of them. In JSON the weekday is its English name in any case, such as
 * {@code "THURSDAY"} or {@code "thursday"}, and the times are {@code "HH:mm"}, such as {@code "06:00"}.
 *
 * @param weekday the day of the week it comes back on
 * @param start the local time it starts at
 * @param end the local time it ends at, after the start on the same day
 */
public record RecurringAwayPeriod(DayOfWeek weekday, LocalTime start, LocalTime end) {

    /** Strict, so that "24:00" is refused rather than read as the midnight that starts the day. */
    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("HH:mm").withResolverStyle(ResolverStyle.STRICT);

    /**
     * Checks that the period stays within its day.
     *
     * @throws IllegalArgumentException when the end is not after the start
     */
    public RecurringAwayPeriod {
        if (!start.isBefore(end)) {
            throw new IllegalArgumentException(
                    "a recurring away period must end after it starts on the same day (no periods over midnight)");
        }
    }

    /** As the config file has it: the weekday and the times as texts. */
    @JsonCreator
    private static RecurringAwayPeriod fromJson(
            @JsonProperty("weekday") String weekday,
            @JsonProperty("start") String start,
            @JsonProperty("end") String end) {
        return new RecurringAwayPeriod(weekday(weekday), time("start", start), time("end", end));
    }

    private static DayOfWeek weekday(String name) {
        var upper = name.toUpperCase(Locale.ROOT);
        if (Arrays.stream(DayOfWeek.values()).noneMatch(day -> day.name().equals(upper))) {
            throw new IllegalArgumentException("weekday must be a day of the week such as MONDAY, not '" + name + "'");
        }
        return DayOfWeek.valueOf(upper);
    }

    private static LocalTime time(String key, String text) {
        try {
            return LocalTime.parse(text, TIME);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(key + " must be a time such as \"06:00\", not '" + text + "'", e);
        }
    }

    /**
     * The away period this one makes on a date. A time that a daylight saving switch skips moves on by the length of
     * the gap, as {@link java.time.ZonedDateTime} does.
     *
     * @param date the local date to look at
     * @param zone the home's time zone, in which the start and end are read
     * @return the period from the start to the end on that date; empty when the date is another day of the week, or
     *     when the switch to summer time leaves the period no time at all
     */
    public Optional<AwayPeriod> on(LocalDate date, ZoneId zone) {
        var from = date.atTime(start).atZone(zone).toInstant();
        var to = date.atTime(end).atZone(zone).toInstant();
        if (date.getDayOfWeek() != weekday || !from.isBefore(to)) {
            return Optional.empty();
        }
        return Optional.of(new AwayPeriod(from, to));
    }
}
