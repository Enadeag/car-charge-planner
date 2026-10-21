package nl.enadeag.carchargeplanner.engine.notify;

import nl.enadeag.carchargeplanner.engine.config.Percent;
import nl.enadeag.carchargeplanner.engine.config.Phases;
import nl.enadeag.carchargeplanner.engine.plan.TripGridCharge;
import nl.enadeag.carchargeplanner.engine.plan.Window;
import nl.enadeag.carchargeplanner.engine.trip.Trip;
import java.time.ZoneId;

/**
 * The grid charge of a trip is to start: soon, or now.
 *
 * @param trip the trip the charge is for
 * @param window the window of the grid charge, with the current to set and the energy it brings
 * @param start whether it starts as planned, or now because little time is left
 * @param zone the time zone of the owner's home, in which the times are meant
 * @param phases the phases the charger charges on
 * @param chargeLimitPct the charge limit to set in the car or the charger, a whole percentage
 */
public record TripChargeStart(Trip trip, Window window, TripGridCharge.Start start, ZoneId zone, Phases phases,
        Percent chargeLimitPct) implements Notice {
}
