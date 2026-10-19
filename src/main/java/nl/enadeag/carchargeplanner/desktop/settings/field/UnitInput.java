package nl.enadeag.carchargeplanner.desktop.settings.field;

import nl.enadeag.carchargeplanner.desktop.app.Units;
import nl.enadeag.carchargeplanner.desktop.settings.form.Quantity;
import nl.enadeag.carchargeplanner.desktop.settings.form.UnitValue;
import nl.enadeag.carchargeplanner.desktop.text.Messages;
import javafx.scene.Node;

/**
 * A number the engine keeps metric, shown in the owner's units. {@link UnitValue} keeps what it stands for when the
 * units change.
 */
public final class UnitInput implements Input {

    private final UnitValue value;
    private final NumberInput field;

    /**
     * Makes the field, in metric units until {@link #showIn(Units)}.
     *
     * @param quantity what the value is, which decides its units and range
     * @param messages writes and reads the numbers in the owner's language
     */
    public UnitInput(Quantity quantity, Messages messages) {
        this.value = new UnitValue(quantity);
        this.field = new NumberInput(value.range(), value.unit(), messages);
    }

    /**
     * Shows a kept value and remembers it as the one not edited.
     *
     * @param kept the value as the engine keeps it, metric
     */
    public void load(double kept) {
        field.load(value.load(kept));
    }

    /**
     * Shows a kept value as if the owner entered it.
     *
     * @param kept the value as the engine keeps it, metric
     */
    public void set(double kept) {
        field.set(value.set(kept));
    }

    /**
     * Shows the value in other units.
     *
     * @param newUnits the units to show it in from now on
     */
    public void showIn(Units newUnits) {
        if (newUnits == value.units()) {
            return;
        }
        commitTyped();
        value.showIn(newUnits);
        field.setRange(value.range());
        field.setUnit(value.unit());
        field.load(value.shownLoaded());
        value.shownEdit().ifPresent(field::set);
    }

    /**
     * The value as the engine keeps it, with what the owner typed but did not confirm yet; the field does not change,
     * see {@link #commitTyped()}.
     *
     * @return the metric value; exactly the loaded or last set one while the field shows it unchanged
     */
    public double value() {
        return value.kept(field.value());
    }

    /**
     * Confirms what the owner typed, as {@link NumberInput#commitTyped()} does, and holds what it stands for, so it
     * stays the same when the units change.
     */
    @Override
    public void commitTyped() {
        field.commitTyped();
        value.hold(field.value());
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
        return value.isChanged(field.value());
    }

    @Override
    public void onChange(Runnable action) {
        field.onChange(action);
    }
}
