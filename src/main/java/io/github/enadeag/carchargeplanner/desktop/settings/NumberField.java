package io.github.enadeag.carchargeplanner.desktop.settings;

import io.github.enadeag.carchargeplanner.desktop.Messages;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.layout.HBox;
import javafx.util.StringConverter;

/**
 * A number the owner types or steps through, with its unit, written the way the owner's language writes numbers. A
 * value outside the range is brought back into it; a text that is not a number keeps the value shown before. A value
 * that was not edited is given back exactly, not as rounded for showing.
 */
final class NumberField implements Input {

    /**
     * What the field allows and how it shows a value.
     *
     * @param min the lowest value
     * @param max the highest value, at least {@code min}
     * @param step how much the arrow keys and buttons change the value, more than 0
     * @param decimals the decimals shown, 0 or more
     */
    record Range(double min, double max, double step, int decimals) {

        /**
         * Checks the values.
         *
         * @throws IllegalArgumentException when the maximum is below the minimum, the step is not more than 0 or the
         *     decimals are negative
         */
        Range {
            if (max < min) {
                throw new IllegalArgumentException("max must be at least min");
            }
            if (step <= 0) {
                throw new IllegalArgumentException("step must be more than 0");
            }
            if (decimals < 0) {
                throw new IllegalArgumentException("decimals must not be negative");
            }
        }
    }

    private final Spinner<Double> spinner = new Spinner<>();
    private final SpinnerValueFactory.DoubleSpinnerValueFactory values;
    private final HBox view;
    private final Label unit = new Label();
    private Range range;
    private double loaded;

    /**
     * Makes the field, showing the range's minimum until {@link #load(double)}.
     *
     * @param range what the field allows and how many decimals it shows
     * @param unit the unit after the field, such as "kWh"; empty for none
     * @param messages writes and reads the numbers in the owner's language
     */
    NumberField(Range range, String unit, Messages messages) {
        this.range = range;
        this.values = new SpinnerValueFactory.DoubleSpinnerValueFactory(range.min(), range.max(), range.min(),
                range.step());
        values.setConverter(new StringConverter<>() {
            @Override
            public String toString(Double value) {
                return value == null ? "" : messages.number(value, NumberField.this.range.decimals());
            }

            @Override
            public Double fromString(String text) {
                var current = values.getValue();
                if (text.equals(toString(current))) {
                    return current; // untouched: keep the exact value, not the rounded one shown
                }
                return messages.parse(text).stream().boxed().findFirst().orElse(current);
            }
        });
        spinner.setValueFactory(values);
        spinner.setEditable(true);
        spinner.getStyleClass().addAll(Spinner.STYLE_CLASS_SPLIT_ARROWS_HORIZONTAL, "number-field");
        if (range.decimals() > 2) {
            spinner.getStyleClass().add("precise"); // such as a latitude: room for "-51.0000"
        }
        this.unit.setText(unit);
        this.unit.getStyleClass().add("field-unit");
        view = new HBox(spinner, this.unit);
        view.getStyleClass().add("number-input");
        loaded = range.min();
    }

    /**
     * Shows a value and remembers it as the one not edited.
     *
     * @param value the value to show; one outside the range is shown at its nearest end, and counts as changed
     */
    void load(double value) {
        loaded = value;
        values.setValue(value);
        spinner.getEditor().setText(values.getConverter().toString(values.getValue()));
    }

    /**
     * Shows a value as if the owner entered it, so it counts as changed when it differs from the loaded one.
     *
     * @param value the value to show; one outside the range is shown at its nearest end
     */
    void set(double value) {
        values.setValue(value);
        spinner.getEditor().setText(values.getConverter().toString(values.getValue()));
    }

    /**
     * Changes what the field allows and shows, such as for other units. The value shown is not changed, except to
     * bring it into the new range.
     *
     * @param range the new range, step and decimals
     */
    void setRange(Range range) {
        this.range = range;
        values.setMin(range.min());
        values.setMax(range.max());
        values.setAmountToStepBy(range.step());
    }

    /**
     * The value now, with what the owner typed but did not confirm yet.
     *
     * @return the value in the range; exactly the loaded one when the owner did not change it
     */
    double value() {
        commitTyped();
        return values.getValue();
    }

    /**
     * The value now, as a whole number.
     *
     * @return {@link #value()} rounded to the nearest whole number
     */
    int wholeValue() {
        return (int) Math.round(value());
    }

    /**
     * Changes the unit shown after the field.
     *
     * @param text the unit, such as "mi"; empty for none
     */
    void setUnit(String text) {
        unit.setText(text);
    }

    @Override
    public Node view() {
        return view;
    }

    /**
     * The spinner, which takes the focus: its own text field only shows that it has it.
     *
     * @return the spinner
     */
    @Override
    public Node focusable() {
        return spinner;
    }

    @Override
    public boolean isChanged() {
        return Double.compare(typed(), loaded) != 0;
    }

    /**
     * The value now, with what the owner typed, without confirming it: that would rewrite the text while the owner
     * types.
     *
     * @return the value in the range; the value shown before while the text is not a number
     */
    double typed() {
        var typed = values.getConverter().fromString(spinner.getEditor().getText());
        return Math.clamp(typed, values.getMin(), values.getMax());
    }

    @Override
    public void onChange(Runnable action) {
        spinner.valueProperty().addListener((property, before, now) -> action.run());
        spinner.getEditor().textProperty().addListener((property, before, now) -> action.run());
    }

    private void commitTyped() {
        var typed = spinner.getEditor().getText();
        var shown = values.getConverter().toString(values.getValue());
        if (!typed.equals(shown)) {
            values.setValue(values.getConverter().fromString(typed));
            spinner.getEditor().setText(values.getConverter().toString(values.getValue()));
        }
    }
}
