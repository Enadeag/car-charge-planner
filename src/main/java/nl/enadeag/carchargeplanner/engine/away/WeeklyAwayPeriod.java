package nl.enadeag.carchargeplanner.engine.away;

import nl.enadeag.carchargeplanner.engine.setting.ConfigSetting;
import nl.enadeag.carchargeplanner.engine.setting.InvalidSetting;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Optional;

/**
 * An away period that comes back every week, such as Thursday 06:00 to 19:00, in the home's local time. It stays
 * within its day: a period over midnight is two of them.
 *
 * @param weekday the day of the week it comes back on
 * @param start the local time it starts at
 * @param end the local time it ends at, after the start on the same day
 */
public record WeeklyAwayPeriod(DayOfWeek weekday, LocalTime start, LocalTime end) {

    /**
     * Checks that the period stays within its day.
     *
     * @throws InvalidSetting when the end is not after the start
     */
    public WeeklyAwayPeriod {
        if (!start.isBefore(end)) {
            throw new InvalidSetting(ConfigSetting.WEEKLY_AWAY_PERIOD, InvalidSetting.Rule.NOT_AFTER_START,
                    "a weekly away period must end after it starts on the same day (no periods over midnight)");
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
    public Optional<OneOffAwayPeriod> on(LocalDate date, ZoneId zone) {
        var from = date.atTime(start).atZone(zone).toInstant();
        var to = date.atTime(end).atZone(zone).toInstant();
        if (date.getDayOfWeek() != weekday || !from.isBefore(to)) {
            return Optional.empty();
        }
        return Optional.of(new OneOffAwayPeriod(from, to));
    }
}
