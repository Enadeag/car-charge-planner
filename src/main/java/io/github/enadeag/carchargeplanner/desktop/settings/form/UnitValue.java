package io.github.enadeag.carchargeplanner.desktop.settings.form;

import io.github.enadeag.carchargeplanner.desktop.app.Units;
import java.util.OptionalDouble;

/**
 * A value the engine keeps metric, as a field shows it in the owner's units. Switching the units does not change what
 * the value stands for: an untouched value stays exactly as kept, and an edited one comes back as typed when the owner
 * switches back, without rounding drift.
 *
 * <p>The value held is the kept value behind what the field shows: the loaded one, the last one set, or what the
 * owner typed when last read.
 */
public final class UnitValue {

    private final Quantity quantity;
    private Units units = Units.METRIC;
    private double loaded;
    private double held;

    /**
     * Makes the value, in metric units until {@link #showIn(Units)}.
     *
     * @param quantity what the value is, which decides its units and range
     */
    public UnitValue(Quantity quantity) {
        this.quantity = quantity;
    }

    /**
     * The units the field shows now.
     *
     * @return metric until {@link #showIn(Units)} chose others
     */
    public Units units() {
        return units;
    }

    /**
     * What the field allows in the units it shows now.
     *
     * @return the range, step and decimals
     */
    public NumberRange range() {
        return quantity.range(units);
    }

    /**
     * The unit after the field, in the units it shows now.
     *
     * @return such as "km" or "mi/kWh"
     */
    public String unit() {
        return quantity.unit(units);
    }

    /**
     * Remembers a kept value as the one not edited, and holds it.
     *
     * @param kept the value as the engine keeps it, metric
     * @return the value for the field to show, in its units
     */
    public double load(double kept) {
        loaded = kept;
        held = kept;
        return quantity.shown(kept, units);
    }

    /**
     * Holds a kept value as if the owner entered it.
     *
     * @param kept the value as the engine keeps it, metric
     * @return the value for the field to show, in its units
     */
    public double set(double kept) {
        held = kept;
        return quantity.shown(kept, units);
    }

    /**
     * Reads what the field shows, and holds what it stands for.
     *
     * @param shown the value in the field, in the units it shows
     * @return the metric value; exactly the held one while the field shows it as it was shown, at its decimals
     */
    public double read(double shown) {
        held = kept(shown);
        return held;
    }

    /**
     * Whether a value in the field differs from the loaded one; holds nothing.
     *
     * @param shown the value in the field, in the units it shows
     * @return true when it stands for another value than the loaded one
     */
    public boolean isChanged(double shown) {
        return Double.compare(kept(shown), loaded) != 0;
    }

    /**
     * Shows the value in other units from now on; what is held does not change. Read what the field shows first, so
     * that a typed value is held.
     *
     * @param newUnits the units to show it in
     */
    public void showIn(Units newUnits) {
        units = newUnits;
    }

    /**
     * The loaded value in the units shown now, for the field to show as the one not edited.
     *
     * @return the loaded value in those units
     */
    public double shownLoaded() {
        return quantity.shown(loaded, units);
    }

    /**
     * The held value in the units shown now, when the owner edited it.
     *
     * @return the held value in those units; empty when it is the loaded one
     */
    public OptionalDouble shownEdit() {
        return Double.compare(held, loaded) != 0 ? OptionalDouble.of(quantity.shown(held, units)) : OptionalDouble.empty();
    }

    /** What a shown value stands for: the held value while the field shows it as it was shown, at its decimals. */
    private double kept(double shown) {
        var decimals = Math.pow(10, quantity.range(units).decimals());
        if (Math.round(shown * decimals) == Math.round(quantity.shown(held, units) * decimals)) {
            return held;
        }
        return quantity.kept(shown, units);
    }
}
