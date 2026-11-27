package nl.enadeag.carchargeplanner.desktop.settings.form;

import nl.enadeag.carchargeplanner.desktop.app.Units;
import nl.enadeag.carchargeplanner.engine.setting.ConfigSetting;

/**
 * A value the engine keeps metric and Settings shows in the owner's units. Consumption converts by the reciprocal:
 * 18 kWh per 100 km is 100 / 18 km per kWh, which is 3.45 miles per kWh.
 */
public enum Quantity {
    /** A distance a day: kept in km, shown in km or miles. */
    DISTANCE(ConfigSetting.CAR_DAILY_DISTANCE, 5, 0, 5, 0),
    /** The car's use of energy: kept in kWh per 100 km, shown so or in miles per kWh. */
    CONSUMPTION(ConfigSetting.CAR_CONSUMPTION, 0.5, 1, 0.1, 2);

    private final ConfigSetting setting;
    private final double metricStep;
    private final int metricDecimals;
    private final double imperialStep;
    private final int imperialDecimals;

    Quantity(ConfigSetting setting, double metricStep, int metricDecimals, double imperialStep, int imperialDecimals) {
        this.setting = setting;
        this.metricStep = metricStep;
        this.metricDecimals = metricDecimals;
        this.imperialStep = imperialStep;
        this.imperialDecimals = imperialDecimals;
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
            case DISTANCE -> units.distance(kept);
            case CONSUMPTION -> 100 / (kept * Units.KM_PER_MILE);
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
            case DISTANCE -> units.km(shown);
            case CONSUMPTION -> 100 / (shown * Units.KM_PER_MILE);
        };
    }

    /**
     * What a field allows in these units: the engine's limit, converted, and the field's own step and decimals.
     *
     * @param units the units the field shows
     * @return the range, step and decimals
     */
    public NumberRange range(Units units) {
        var limit = Limit.of(setting);
        if (units == Units.METRIC) {
            return NumberRange.of(limit, metricStep, metricDecimals);
        }
        return NumberRange.of(limit.converted(kept -> shown(kept, units)), imperialStep, imperialDecimals);
    }

    /**
     * The unit's symbol, the same in every language.
     *
     * @param units the units the field shows
     * @return such as "km" or "mi/kWh"
     */
    public String unit(Units units) {
        return switch (this) {
            case DISTANCE -> units.distanceUnit();
            case CONSUMPTION -> units == Units.METRIC ? "kWh/100 km" : "mi/kWh";
        };
    }
}
