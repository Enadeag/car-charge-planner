package nl.enadeag.carchargeplanner.engine.notify;

import java.time.ZoneId;

/**
 * Something the engine tells the owner, which the desktop app shows as a notification: a charge window that starts
 * soon, or what a trip asks of the owner.
 */
public sealed interface Notice permits Announcement, TripChargeStart, BatteryCheck, TripShort {

    /**
     * The time zone the notice's times are meant in.
     *
     * @return the time zone of the owner's home
     */
    ZoneId zone();
}
