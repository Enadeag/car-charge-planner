package nl.enadeag.carchargeplanner.desktop.settings.calendar;

import nl.enadeag.carchargeplanner.engine.calendar.CalendarRead;

/** Reads the calendar as saved, at once, for the days the plan covers. */
@FunctionalInterface
public interface CalendarReading {

    /**
     * Reads the calendar; blocks while the forecast and the calendar are fetched, so call it away from the JavaFX
     * thread.
     *
     * @return how many events were read, why the calendar could not be read, or why it was not read
     */
    CalendarRead readNow();
}
