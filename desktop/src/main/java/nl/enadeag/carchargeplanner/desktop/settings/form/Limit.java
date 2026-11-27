package nl.enadeag.carchargeplanner.desktop.settings.form;

import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.desktop.text.NumberText;
import nl.enadeag.carchargeplanner.desktop.text.TextKey;
import nl.enadeag.carchargeplanner.engine.config.Car;
import nl.enadeag.carchargeplanner.engine.config.Charger;
import nl.enadeag.carchargeplanner.engine.config.Coordinates;
import nl.enadeag.carchargeplanner.engine.config.House;
import nl.enadeag.carchargeplanner.engine.config.PanelArray;
import nl.enadeag.carchargeplanner.engine.config.Percent;
import nl.enadeag.carchargeplanner.engine.config.PlanningPreferences;
import nl.enadeag.carchargeplanner.engine.setting.ConfigSetting;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.function.DoubleUnaryOperator;

/**
 * What the engine allows for a typed setting, in the unit its field shows, so the field can say it and its arrows can
 * stop at it. The ends come from the engine's records ({@link #of(ConfigSetting)}); the form has no ranges of its own.
 *
 * @param low the lowest end, finite
 * @param lowIncluded whether {@code low} itself is allowed
 * @param high the highest end, above {@code low}; positive infinity for none
 * @param highIncluded whether {@code high} itself is allowed; false when there is no highest end
 */
public record Limit(double low, boolean lowIncluded, double high, boolean highIncluded) {

    private static final double HOURS_PER_DAY = 24;
    private static final double PERCENT_PER_SHARE = 100;

    /**
     * Checks the ends.
     *
     * @throws IllegalArgumentException when the lowest end is not finite, the highest is not above it, or no highest
     *     end is said to be included
     */
    public Limit {
        if (!Double.isFinite(low)) {
            throw new IllegalArgumentException("low must be finite, not " + low);
        }
        if (!(high > low)) {
            throw new IllegalArgumentException("high must be above low, not " + high);
        }
        if (highIncluded && Double.isInfinite(high)) {
            throw new IllegalArgumentException("an end that is not there cannot be included");
        }
    }

    /**
     * From one end to the other, both allowed.
     *
     * @param low the lowest value allowed, finite
     * @param high the highest value allowed, above {@code low} and finite
     * @return the limit
     */
    public static Limit fromTo(double low, double high) {
        return new Limit(low, true, high, true);
    }

    /**
     * A lowest end that is allowed, and no highest.
     *
     * @param low the lowest value allowed, finite
     * @return the limit
     */
    public static Limit atLeast(double low) {
        return new Limit(low, true, Double.POSITIVE_INFINITY, false);
    }

    /**
     * Above a lowest end that is not allowed itself, and no highest.
     *
     * @param low the value every allowed one is above, finite
     * @return the limit
     */
    public static Limit moreThan(double low) {
        return new Limit(low, false, Double.POSITIVE_INFINITY, false);
    }

    /**
     * What the engine allows for a setting, in the unit its field shows: a share in %, the household use in kWh per
     * day, a distance in km and the consumption in kWh per 100 km.
     *
     * @param setting a setting the owner types a number for
     * @return the limit
     * @throws IllegalArgumentException for a setting that is chosen rather than typed, or has a rule of its own, such
     *     as the panels' number
     */
    public static Limit of(ConfigSetting setting) {
        return switch (setting) {
            case LATITUDE -> fromTo(Coordinates.MIN_LATITUDE, Coordinates.MAX_LATITUDE);
            case LONGITUDE -> fromTo(Coordinates.MIN_LONGITUDE, Coordinates.MAX_LONGITUDE);
            case PANEL_LOSSES -> new Limit(PanelArray.MIN_SYSTEM_LOSSES, true, PanelArray.SYSTEM_LOSSES_BELOW, false)
                    .scaled(PERCENT_PER_SHARE);
            case HOUSE_BASELOAD -> atLeast(House.MIN_BASELOAD_KW).scaled(HOURS_PER_DAY);
            case CAR_BATTERY -> fromTo(Car.MIN_USABLE_BATTERY_KWH, Car.MAX_USABLE_BATTERY_KWH);
            case CAR_CONSUMPTION -> fromTo(Car.MIN_CONSUMPTION_KWH_PER_100KM, Car.MAX_CONSUMPTION_KWH_PER_100KM);
            case CAR_DAILY_DISTANCE -> fromTo(Car.MIN_KM_PER_DAY, Car.MAX_KM_PER_DAY);
            case CAR_TRIP_SURCHARGE, MINIMUM_CHARGE, TARGET_CHARGE -> fromTo(Percent.MIN, Percent.MAX);
            case CHARGER_MIN_CURRENT, CHARGER_MAX_CURRENT -> fromTo(Charger.MIN_CURRENT_AMPS, Charger.MAX_CURRENT_AMPS);
            case CHARGER_VOLTAGE -> fromTo(Charger.MIN_VOLTS, Charger.MAX_VOLTS);
            case CHARGER_EFFICIENCY -> fromTo(Charger.MIN_EFFICIENCY, Charger.MAX_EFFICIENCY).scaled(PERCENT_PER_SHARE);
            case PLANNING_MARGIN -> new Limit(PlanningPreferences.MARGIN_ABOVE, false, PlanningPreferences.MAX_MARGIN,
                    true).scaled(PERCENT_PER_SHARE);
            case IMPORT_TOLERANCE -> atLeast(PlanningPreferences.MIN_IMPORT_TOLERANCE_KWH);
            case MINIMUM_WINDOW -> moreThan(PlanningPreferences.MIN_WINDOW_ABOVE_MINUTES);
            case PANEL_SIZE, PANEL_COUNT, PANEL_POWER, PANEL_YEAR, PANEL_TILT, PANEL_DIRECTION, CHARGER_PHASES,
                    PRIMARY_PROVIDER, FALLBACK_PROVIDER, WEEKLY_AWAY_PERIOD, NOTIFY_LEAD, CHARGING_HOURS ->
                throw new IllegalArgumentException(setting + " is not a number the owner types against a limit");
        };
    }

    /**
     * The same limit in a unit that is a multiple of this one, worked out exactly in decimals.
     *
     * @param factor how many of the new unit make one of this, more than 0, such as 100 for a share in %
     * @return the limit in the new unit
     */
    public Limit scaled(double factor) {
        var times = BigDecimal.valueOf(factor);
        var newHigh = Double.isInfinite(high) ? high : BigDecimal.valueOf(high).multiply(times).doubleValue();
        return new Limit(BigDecimal.valueOf(low).multiply(times).doubleValue(), lowIncluded, newHigh, highIncluded);
    }

    /**
     * The same limit in another unit, such as miles for km, whose conversion may turn the ends round, as miles per
     * kWh does to kWh per 100 km.
     *
     * @param convert turns a value of this unit into the other unit; it keeps the order of values or reverses it
     * @return the limit in the other unit
     * @throws IllegalStateException when an end is open or not allowed itself, which a conversion does not keep
     */
    public Limit converted(DoubleUnaryOperator convert) {
        if (!lowIncluded || !highIncluded) {
            throw new IllegalStateException("only a limit with both ends allowed is converted");
        }
        var one = convert.applyAsDouble(low);
        var other = convert.applyAsDouble(high);
        return fromTo(Math.min(one, other), Math.max(one, other));
    }

    /**
     * The lowest value a field with these decimals can show that the limit allows, where its arrows stop.
     *
     * @param decimals the decimals the field shows, 0 or more
     * @return the lowest end rounded up to the decimals, or one decimal step above it when it is not allowed itself
     */
    public double lowest(int decimals) {
        var rounded = BigDecimal.valueOf(low).setScale(decimals, RoundingMode.CEILING);
        if (!lowIncluded && rounded.doubleValue() <= low) {
            rounded = rounded.add(BigDecimal.ONE.movePointLeft(decimals));
        }
        return rounded.doubleValue();
    }

    /**
     * The highest value a field with these decimals can show that the limit allows, where its arrows stop.
     *
     * @param decimals the decimals the field shows, 0 or more
     * @return the highest end rounded down to the decimals, or one decimal step below it when it is not allowed
     *     itself; positive infinity when there is no highest end
     */
    public double highest(int decimals) {
        if (Double.isInfinite(high)) {
            return high;
        }
        var rounded = BigDecimal.valueOf(high).setScale(decimals, RoundingMode.FLOOR);
        if (!highIncluded && rounded.doubleValue() >= high) {
            rounded = rounded.subtract(BigDecimal.ONE.movePointLeft(decimals));
        }
        return rounded.doubleValue();
    }

    /**
     * What a field says about a value outside the limit: an end that is allowed is named as the field can show it,
     * rounded inward, so a value the text names is always accepted; an end that is not allowed is named as it is.
     *
     * @param decimals the decimals the field shows, 0 or more
     * @param messages the texts, in the owner's language, which write the ends as a field writes numbers
     * @return such as "Type a number from 0 to 621."
     */
    public String text(int decimals, Messages messages) {
        var from = written(lowIncluded ? lowest(decimals) : low, decimals, messages);
        if (Double.isInfinite(high)) {
            return messages.text(lowIncluded ? TextKey.PROBLEM_AT_LEAST : TextKey.PROBLEM_MORE_THAN, from);
        }
        var to = written(highIncluded ? highest(decimals) : high, decimals, messages);
        if (!lowIncluded) {
            return messages.text(TextKey.PROBLEM_MORE_THAN_AT_MOST, from, to);
        }
        return messages.text(highIncluded ? TextKey.PROBLEM_FROM_TO : TextKey.PROBLEM_AT_LEAST_BELOW, from, to);
    }

    /** Without the decimals a whole end does not need: "1000", not "1000.0". */
    private static String written(double value, int decimals, Messages messages) {
        var needed = Math.max(0, BigDecimal.valueOf(value).stripTrailingZeros().scale());
        return new NumberText(messages).write(value, Math.min(needed, decimals));
    }
}
