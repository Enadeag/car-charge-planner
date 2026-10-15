package io.github.enadeag.carchargeplanner.desktop.settings;

import io.github.enadeag.carchargeplanner.desktop.Messages;
import io.github.enadeag.carchargeplanner.desktop.Units;
import javafx.scene.Node;

/**
 * A number the engine keeps metric, shown in the owner's units. Switching the units does not change what the value
 * stands for: an untouched value stays exactly as kept, and an edited one comes back as typed when the owner switches
 * back, without rounding drift.
 */
final class UnitField implements Input {

    private final Quantity quantity;
    private final NumberField field;
    private Units units;
    private double loaded;
    private double held;

    /**
     * Makes the field, in metric units until {@link #showIn(Units)}.
     *
     * @param quantity what the value is, which decides its units and range
     * @param messages writes and reads the numbers in the owner's language
     */
    UnitField(Quantity quantity, Messages messages) {
        this.quantity = quantity;
        this.units = Units.METRIC;
        this.field = new NumberField(quantity.range(units), quantity.unit(units), messages);
    }

    /**
     * Shows a kept value and remembers it as the one not edited.
     *
     * @param kept the value as the engine keeps it, metric
     */
    void load(double kept) {
        loaded = kept;
        held = kept;
        field.load(quantity.shown(kept, units));
    }

    /**
     * Shows a kept value as if the owner entered it.
     *
     * @param kept the value as the engine keeps it, metric
     */
    void set(double kept) {
        held = kept;
        field.set(quantity.shown(kept, units));
    }

    /**
     * Shows the value in other units.
     *
     * @param newUnits the units to show it in from now on
     */
    void showIn(Units newUnits) {
        if (newUnits == units) {
            return;
        }
        var kept = value();
        units = newUnits;
        field.setRange(quantity.range(units));
        field.setUnit(quantity.unit(units));
        field.load(quantity.shown(loaded, units));
        held = kept;
        if (Double.compare(kept, loaded) != 0) {
            field.set(quantity.shown(kept, units));
        }
    }

    /**
     * The value as the engine keeps it.
     *
     * @return the metric value; exactly the loaded or last set one while the field shows it unchanged
     */
    double value() {
        held = kept(field.value());
        return held;
    }

    @Override
    public Node view() {
        return field.view();
    }

    @Override
    public Node focusable() {
        return field.focusable();
    }

    @Override
    public boolean isChanged() {
        return Double.compare(kept(field.typed()), loaded) != 0;
    }

    @Override
    public void onChange(Runnable action) {
        field.onChange(action);
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
