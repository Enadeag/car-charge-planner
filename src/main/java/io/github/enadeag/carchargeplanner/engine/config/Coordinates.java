package io.github.enadeag.carchargeplanner.engine.config;

import io.github.enadeag.carchargeplanner.engine.setting.InvalidSetting;
import io.github.enadeag.carchargeplanner.engine.setting.Setting;

/**
 * A place on earth in degrees.
 *
 * @param latitude the latitude in degrees, from -90 to 90, north positive
 * @param longitude the longitude in degrees, from -180 to 180, east positive
 */
public record Coordinates(double latitude, double longitude) {

    /**
     * Checks the ranges.
     *
     * @throws InvalidSetting when the latitude or the longitude is out of its range or not a number
     */
    public Coordinates {
        if (!isLatitude(latitude)) {
            throw new InvalidSetting(Setting.LATITUDE, InvalidSetting.Rule.OUT_OF_RANGE,
                    "latitude must be between -90 and 90");
        }
        if (!isLongitude(longitude)) {
            throw new InvalidSetting(Setting.LONGITUDE, InvalidSetting.Rule.OUT_OF_RANGE,
                    "longitude must be between -180 and 180");
        }
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
        return degrees >= -90 && degrees <= 90;
    }

    private static boolean isLongitude(double degrees) {
        return degrees >= -180 && degrees <= 180;
    }
}
