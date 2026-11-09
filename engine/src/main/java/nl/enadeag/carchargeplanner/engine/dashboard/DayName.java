package nl.enadeag.carchargeplanner.engine.dashboard;

import java.time.LocalDate;

/**
 * How a day is named, from the day the plan was made in the home's time zone: today, tomorrow, or any other day by its
 * weekday or date. Each client says it in its own words.
 */
public sealed interface DayName {

    /**
     * The day that is named.
     *
     * @return the date, in the home's time zone
     */
    LocalDate date();

    /**
     * The name of a day.
     *
     * @param date the day to name
     * @param today the day the plan was made, in the home's time zone
     * @return {@link Today} for {@code today}, {@link Tomorrow} for the day after, {@link Other} for any other day
     */
    static DayName of(LocalDate date, LocalDate today) {
        if (date.equals(today)) {
            return new Today(date);
        }
        if (date.equals(today.plusDays(1))) {
            return new Tomorrow(date);
        }
        return new Other(date);
    }

    /**
     * The day the plan was made.
     *
     * @param date the day
     */
    record Today(LocalDate date) implements DayName {
    }

    /**
     * The day after the day the plan was made.
     *
     * @param date the day
     */
    record Tomorrow(LocalDate date) implements DayName {
    }

    /**
     * Any other day, named by its weekday or date.
     *
     * @param date the day
     */
    record Other(LocalDate date) implements DayName {
    }
}
