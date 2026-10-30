package nl.enadeag.carchargeplanner.desktop.app;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

/**
 * How distances and consumption are shown. The engine keeps them metric; only what the owner sees and types changes.
 * Energy and power are the same in both.
 */
public enum Units {
    /** Kilometres, and kWh per 100 km. */
    METRIC("metric"),
    /** Miles, and miles per kWh. */
    IMPERIAL("imperial");

    /** The length of a mile in km. */
    public static final double KM_PER_MILE = 1.609344;

    /** The countries that still measure road distances in miles. */
    private static final Set<String> MILES = Set.of("US", "GB", "LR", "MM");

    private final String code;

    Units(String code) {
        this.code = code;
    }

    /**
     * The name the app settings file writes.
     *
     * @return such as "metric"
     */
    public String code() {
        return code;
    }

    /**
     * The units with this name in the app settings file.
     *
     * @param code such as "imperial", in any case; may be empty or unknown
     * @return the units, or empty when there are none with the name
     */
    public static Optional<Units> ofCode(String code) {
        return Arrays.stream(values()).filter(units -> units.code.equalsIgnoreCase(code)).findFirst();
    }

    /**
     * A distance as the owner sees it.
     *
     * @param km the distance as the engine keeps it, in km
     * @return the distance in these units: km or miles
     */
    public double distance(double km) {
        return this == METRIC ? km : km / KM_PER_MILE;
    }

    /**
     * A distance as the engine keeps it.
     *
     * @param distance a distance in these units, such as one the owner typed
     * @return the distance in km
     */
    public double km(double distance) {
        return this == METRIC ? distance : distance * KM_PER_MILE;
    }

    /**
     * The symbol of a distance in these units, the same in every language.
     *
     * @return "km" or "mi"
     */
    public String distanceUnit() {
        return this == METRIC ? "km" : "mi";
    }

    /**
     * The units the owner's country measures roads in, for an owner who has not chosen.
     *
     * @param country an ISO 3166 country code such as "GB", from the system's locale; may be empty
     * @return imperial in the United States, the United Kingdom, Liberia and Myanmar, metric elsewhere
     */
    public static Units ofCountry(String country) {
        return MILES.contains(country) ? IMPERIAL : METRIC;
    }
}
