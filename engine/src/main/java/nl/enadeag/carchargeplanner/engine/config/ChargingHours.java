package nl.enadeag.carchargeplanner.engine.config;

import nl.enadeag.carchargeplanner.engine.setting.ConfigSetting;
import nl.enadeag.carchargeplanner.engine.setting.InvalidSetting;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * The hours in which the owner can start a charge, in the home's local time, both ends included: from 07:00 to 22:00
 * a charge may start at 22:00 but not at 22:05. An end before the start spans midnight, as 20:00 to 02:00 does.
 *
 * @param start the earliest local time a charge may start, to the minute
 * @param end the latest local time a charge may start, to the minute; not the start
 */
public record ChargingHours(LocalTime start, LocalTime end) {

    /** The charging hours unless the owner changes them: 07:00 to 22:00. */
    public static final ChargingHours STANDARD = new ChargingHours(LocalTime.of(7, 0), LocalTime.of(22, 0));

    /**
     * Checks the times.
     *
     * @throws InvalidSetting when the end is the start
     * @throws IllegalArgumentException when a time is not a whole minute, which the config file cannot hold
     */
    public ChargingHours {
        if (start.equals(end)) {
            throw new InvalidSetting(ConfigSetting.CHARGING_HOURS, InvalidSetting.Rule.SAME_AS_START,
                    "chargingHours must end at another time than they start");
        }
        if (start.getSecond() != 0 || start.getNano() != 0) {
            throw new IllegalArgumentException("chargingHours start must be a whole minute, not " + start);
        }
        if (end.getSecond() != 0 || end.getNano() != 0) {
            throw new IllegalArgumentException("chargingHours end must be a whole minute, not " + end);
        }
    }

    /**
     * Whether a charge may start at a local time.
     *
     * @param time the local time
     * @return true from the start to the end, both included
     */
    public boolean allowStart(LocalTime time) {
        if (start.isBefore(end)) {
            return !time.isBefore(start) && !time.isAfter(end);
        }
        return !time.isBefore(start) || !time.isAfter(end);
    }

    /**
     * Whether a charge may start at a moment.
     *
     * @param moment the moment
     * @param zone the home's time zone, in which the hours are meant
     * @return true when the local time of the moment is within the hours
     */
    public boolean allowStart(Instant moment, ZoneId zone) {
        return allowStart(moment.atZone(zone).toLocalTime());
    }

    /**
     * The first moment a charge may start, from a moment on.
     *
     * @param moment the moment from which on to look
     * @param zone the home's time zone, in which the hours are meant
     * @return the moment itself when it is within the hours, otherwise the next start of the hours; a start that a
     *     daylight saving switch skips moves on by the length of the gap
     */
    public Instant nextStart(Instant moment, ZoneId zone) {
        if (allowStart(moment, zone)) {
            return moment;
        }
        var local = moment.atZone(zone);
        var today = local.toLocalDate().atTime(start).atZone(zone).toInstant();
        return today.isAfter(moment) ? today : local.toLocalDate().plusDays(1).atTime(start).atZone(zone).toInstant();
    }
}
