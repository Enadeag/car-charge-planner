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
     * The units the owner's country measures roads in, for an owner who has not chosen.
     *
     * @param country an ISO 3166 country code such as "GB", from the system's locale; may be empty
     * @return imperial in the United States, the United Kingdom, Liberia and Myanmar, metric elsewhere
     */
    public static Units ofCountry(String country) {
        return MILES.contains(country) ? IMPERIAL : METRIC;
    }
}
