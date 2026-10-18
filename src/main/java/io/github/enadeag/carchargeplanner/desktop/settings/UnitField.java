package io.github.enadeag.carchargeplanner.desktop.settings;

import io.github.enadeag.carchargeplanner.desktop.app.Units;
import io.github.enadeag.carchargeplanner.desktop.settings.form.Quantity;
import io.github.enadeag.carchargeplanner.desktop.settings.form.UnitValue;
import io.github.enadeag.carchargeplanner.desktop.text.Messages;
import javafx.scene.Node;

/**
 * A number the engine keeps metric, shown in the owner's units. {@link UnitValue} keeps what it stands for when the
 * units change.
 */
final class UnitField implements Input {

    private final UnitValue value;
    private final NumberField field;

    /**
     * Makes the field, in metric units until {@link #showIn(Units)}.
     *
     * @param quantity what the value is, which decides its units and range
     * @param messages writes and reads the numbers in the owner's language
     */
    UnitField(Quantity quantity, Messages messages) {
        this.value = new UnitValue(quantity);
        this.field = new NumberField(value.range(), value.unit(), messages);
    }

    /**
     * Shows a kept value and remembers it as the one not edited.
     *
     * @param kept the value as the engine keeps it, metric
     */
    void load(double kept) {
        field.load(value.load(kept));
    }

    /**
     * Shows a kept value as if the owner entered it.
     *
     * @param kept the value as the engine keeps it, metric
     */
    void set(double kept) {
        field.set(value.set(kept));
    }

    /**
     * Shows the value in other units.
     *
     * @param newUnits the units to show it in from now on
     */
    void showIn(Units newUnits) {
        if (newUnits == value.units()) {
            return;
        }
        value(); // holds what the owner typed
        value.showIn(newUnits);
        field.setRange(value.range());
        field.setUnit(value.unit());
        field.load(value.shownLoaded());
        value.shownEdit().ifPresent(field::set);
    }

    /**
     * The value as the engine keeps it.
     *
     * @return the metric value; exactly the loaded or last set one while the field shows it unchanged
     */
    double value() {
        return value.read(field.value());
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
        return value.isChanged(field.typed());
    }

    @Override
    public void onChange(Runnable action) {
        field.onChange(action);
    }
}
