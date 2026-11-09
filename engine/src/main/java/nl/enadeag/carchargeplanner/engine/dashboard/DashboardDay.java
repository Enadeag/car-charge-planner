package nl.enadeag.carchargeplanner.engine.dashboard;

/**
 * One day of the plan as the dashboard shows it, in the home's time zone.
 *
 * @param name how the day is named, never null; it also holds the day's date, in the home's time zone
 * @param chart the day's expected solar power, the baseload and the charge windows, never null
 */
public record DashboardDay(DayName name, DayChart chart) {
}
