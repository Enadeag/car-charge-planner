package nl.enadeag.carchargeplanner.engine.notify;

import nl.enadeag.carchargeplanner.engine.config.Percent;
import nl.enadeag.carchargeplanner.engine.trip.Trip;
import java.time.ZoneId;

/**
 * A trip depends on a battery level the owner has not entered lately: is it right?
 *
 * @param trip the trip that needs charging or is tight
 * @param levelPct the battery level the plan starts from
 * @param zone the time zone of the owner's home, in which the times are meant
 */
public record BatteryCheck(Trip trip, Percent levelPct, ZoneId zone) implements Notice {
}
