package nl.enadeag.carchargeplanner.desktop.settings.field;

import nl.enadeag.carchargeplanner.desktop.settings.form.NumberRange;
import nl.enadeag.carchargeplanner.desktop.settings.form.NumberText;
import nl.enadeag.carchargeplanner.desktop.text.Messages;
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
public final class NumberInput implements Input {

    private final Spinner<Double> spinner = new Spinner<>();
    private final SpinnerValueFactory.DoubleSpinnerValueFactory values;
    private final HBox view;
    private final Label unit = new Label();
    private NumberRange range;
    private double loaded;

    /**
     * Makes the field, showing the range's minimum until {@link #load(double)}.
     *
     * @param range what the field allows and how many decimals it shows
     * @param unit the unit after the field, such as "kWh"; empty for none
     * @param messages writes and reads the numbers in the owner's language
     */
    public NumberInput(NumberRange range, String unit, Messages messages) {
        this.range = range;
        this.values = new SpinnerValueFactory.DoubleSpinnerValueFactory(range.min(), range.max(), range.min(),
                range.step());
        var text = new NumberText(messages);
        values.setConverter(new StringConverter<>() {
            @Override
            public String toString(Double value) {
                return value == null ? "" : text.write(value, NumberInput.this.range.decimals());
            }

            @Override
            public Double fromString(String typed) {
                return text.read(typed, values.getValue(), NumberInput.this.range.decimals());
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
    public void load(double value) {
        loaded = value;
        values.setValue(value);
        spinner.getEditor().setText(values.getConverter().toString(values.getValue()));
    }

    /**
     * Shows a value as if the owner entered it, so it counts as changed when it differs from the loaded one.
     *
     * @param value the value to show; one outside the range is shown at its nearest end
     */
    public void set(double value) {
        values.setValue(value);
        spinner.getEditor().setText(values.getConverter().toString(values.getValue()));
    }

    /**
     * Changes what the field allows and shows, such as for other units. The value shown is not changed, except to
     * bring it into the new range.
     *
     * @param range the new range, step and decimals
     */
    void setRange(NumberRange range) {
        this.range = range;
        values.setMin(range.min());
        values.setMax(range.max());
        values.setAmountToStepBy(range.step());
    }

    /**
     * The value now, with what the owner typed but did not confirm yet; the field does not change, see
     * {@link #commitTyped()}.
     *
     * @return the value in the range; the value shown before while the text is not a number; exactly the loaded one
     *     when the owner did not change it
     */
    public double value() {
        return range.clamp(values.getConverter().fromString(spinner.getEditor().getText()));
    }

    /**
     * Confirms what the owner typed, as Enter does: the field then shows it as it writes numbers, brought into the
     * range, and a text that is not a number gives way to the value shown before. Not done while the owner types, as
     * it rewrites the text.
     */
    @Override
    public void commitTyped() {
        var typed = spinner.getEditor().getText();
        var shown = values.getConverter().toString(values.getValue());
        if (!typed.equals(shown)) {
            values.setValue(values.getConverter().fromString(typed));
            spinner.getEditor().setText(values.getConverter().toString(values.getValue()));
        }
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
        return Double.compare(value(), loaded) != 0;
    }

    @Override
    public void onChange(Runnable action) {
        spinner.valueProperty().addListener((_, _, _) -> action.run());
        spinner.getEditor().textProperty().addListener((_, _, _) -> action.run());
    }
}
