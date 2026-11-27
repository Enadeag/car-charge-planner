package nl.enadeag.carchargeplanner.engine.config;

import nl.enadeag.carchargeplanner.engine.setting.ConfigSetting;
import nl.enadeag.carchargeplanner.engine.setting.InvalidSetting;

/**
 * A place on earth in degrees.
 *
 * @param latitude the latitude in degrees, from {@link #MIN_LATITUDE} to {@link #MAX_LATITUDE}, north positive
 * @param longitude the longitude in degrees, from {@link #MIN_LONGITUDE} to {@link #MAX_LONGITUDE}, east positive
 */
public record Coordinates(double latitude, double longitude) {

    /** The lowest latitude, in degrees: the South Pole. */
    public static final int MIN_LATITUDE = -90;

    /** The highest latitude, in degrees: the North Pole. */
    public static final int MAX_LATITUDE = 90;

    /** The lowest longitude, in degrees, west of Greenwich. */
    public static final int MIN_LONGITUDE = -180;

    /** The highest longitude, in degrees, east of Greenwich. */
    public static final int MAX_LONGITUDE = 180;

    /**
     * Where a first-run draft puts the home until the owner chose a place: 0, 0 lies in the ocean, so no home is
     * there (see "Not set up" in CONTEXT.md).
     */
    public static final Coordinates NOT_CHOSEN = new Coordinates(0, 0);

    /**
     * Checks the ranges.
     *
     * @throws InvalidSetting when the latitude or the longitude is out of its range or not a number
     */
    public Coordinates {
        if (!isLatitude(latitude)) {
            throw new InvalidSetting(ConfigSetting.LATITUDE, InvalidSetting.Rule.OUT_OF_RANGE,
                    "latitude must be between " + MIN_LATITUDE + " and " + MAX_LATITUDE);
        }
        if (!isLongitude(longitude)) {
            throw new InvalidSetting(ConfigSetting.LONGITUDE, InvalidSetting.Rule.OUT_OF_RANGE,
                    "longitude must be between " + MIN_LONGITUDE + " and " + MAX_LONGITUDE);
        }
    }

    /**
     * Whether these are a place the owner chose, rather than {@link #NOT_CHOSEN}.
     *
     * @return false when both the latitude and the longitude are 0
     */
    public boolean isChosen() {
        return latitude != 0 || longitude != 0;
    }

    /**
     * Whether two numbers are a latitude and a longitude, for checks that drop what is off the map instead of failing.
     *
     * @param latitude the latitude to check in degrees
     * @param longitude the longitude to check in degrees
     * @return true when the latitude is from -90 to 90 and the longitude from -180 to 180
     */
    public static boolean areValid(double latitude, double longitude) {
        return isLatitude(latitude) && isLongitude(longitude);
    }

    private static boolean isLatitude(double degrees) {
        return degrees >= MIN_LATITUDE && degrees <= MAX_LATITUDE;
    }

    private static boolean isLongitude(double degrees) {
        return degrees >= MIN_LONGITUDE && degrees <= MAX_LONGITUDE;
    }
}
