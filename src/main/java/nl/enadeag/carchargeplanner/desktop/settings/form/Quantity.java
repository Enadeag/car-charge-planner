package nl.enadeag.carchargeplanner.desktop.settings.form;

import nl.enadeag.carchargeplanner.desktop.app.Units;

/**
 * A value the engine keeps metric and Settings shows in the owner's units. Consumption converts by the reciprocal:
 * 18 kWh per 100 km is 100 / 18 km per kWh, which is 3.45 miles per kWh.
 */
public enum Quantity {
    /** A distance a day: kept in km, shown in km or miles. */
    DISTANCE(new NumberRange(0, 1000, 5, 0), new NumberRange(0, 620, 5, 0)),
    /** The car's use of energy: kept in kWh per 100 km, shown so or in miles per kWh. */
    CONSUMPTION(new NumberRange(5, 60, 0.5, 1), new NumberRange(1, 12.5, 0.1, 2));

    private static final double KM_PER_MILE = 1.609344;

    private final NumberRange metric;
    private final NumberRange imperial;

    Quantity(NumberRange metric, NumberRange imperial) {
        this.metric = metric;
        this.imperial = imperial;
    }

    /**
     * The value as the owner sees it.
     *
     * @param kept the value as the engine keeps it, metric; a consumption more than 0
     * @param units the units to show it in
     * @return the value in those units
     */
    public double shown(double kept, Units units) {
        if (units == Units.METRIC) {
            return kept;
        }
        return switch (this) {
            case DISTANCE -> kept / KM_PER_MILE;
            case CONSUMPTION -> 100 / (kept * KM_PER_MILE);
        };
    }

    /**
     * The value as the engine keeps it.
     *
     * @param shown the value the owner sees or typed; a consumption more than 0
     * @param units the units it is in
     * @return the metric value
     */
    public double kept(double shown, Units units) {
        if (units == Units.METRIC) {
            return shown;
        }
        return switch (this) {
            case DISTANCE -> shown * KM_PER_MILE;
            case CONSUMPTION -> 100 / (shown * KM_PER_MILE);
        };
    }

    /**
     * What a field allows in these units.
     *
     * @param units the units the field shows
     * @return the range, step and decimals
     */
    public NumberRange range(Units units) {
        return units == Units.METRIC ? metric : imperial;
    }

    /**
     * The unit's symbol, the same in every language.
     *
     * @param units the units the field shows
     * @return such as "km" or "mi/kWh"
     */
    public String unit(Units units) {
        return switch (this) {
            case DISTANCE -> units == Units.METRIC ? "km" : "mi";
            case CONSUMPTION -> units == Units.METRIC ? "kWh/100 km" : "mi/kWh";
        };
    }
}
