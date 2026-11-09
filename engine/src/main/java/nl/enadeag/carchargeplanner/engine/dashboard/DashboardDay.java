package nl.enadeag.carchargeplanner.engine.dashboard;

import java.time.LocalDate;

/**
 * One day of the plan as the dashboard shows it, in the home's time zone.
 *
 * @param date the day, in the home's time zone
 * @param name how the day is named, for {@code date}
 * @param chart the day's expected solar power, the house's use and the charge windows
 */
public record DashboardDay(LocalDate date, DayName name, DayChart chart) {

    /**
     * Checks that the name is the day's.
     *
     * @throws IllegalArgumentException when the name is of another date
     */
    public DashboardDay {
        if (!name.date().equals(date)) {
            throw new IllegalArgumentException("a day's name must be of " + date + ", not of " + name.date());
        }
    }
}
