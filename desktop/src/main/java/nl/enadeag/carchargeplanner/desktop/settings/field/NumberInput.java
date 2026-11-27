package nl.enadeag.carchargeplanner.desktop.settings.field;

import nl.enadeag.carchargeplanner.desktop.settings.form.NumberRange;
import nl.enadeag.carchargeplanner.desktop.settings.form.NumberText;
import nl.enadeag.carchargeplanner.desktop.text.Messages;
import java.math.BigDecimal;
import java.util.Optional;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.layout.HBox;
import javafx.util.StringConverter;

/**
 * A number the owner types or steps through, with its unit, written the way the owner's language writes numbers.
 * Nothing is brought into a range: a typed value stays as typed, and the engine's records say whether it is allowed.
 * The arrows stop at the engine's limit. A text that is not a number the field takes stays as typed, and the field
 * says what is wrong with it. A value that was not edited is given back exactly, not as rounded for showing.
 */
public final class NumberInput implements NumberField {

    private final Spinner<Double> spinner = new Spinner<>();
    private final Values values = new Values();
    private final HBox view;
    private final Label unit = new Label();
    private final Messages messages;
    private final NumberText text;
    private NumberRange range;
    private double loaded;

    /**
     * Makes the field, showing 0 until {@link #load(double)}, or the lowest value the limit allows when 0 is not.
     *
     * @param range what the engine allows and how the field shows and steps
     * @param unit the unit after the field, such as "kWh"; empty for none
     * @param messages writes and reads the numbers in the owner's language
     */
    public NumberInput(NumberRange range, String unit, Messages messages) {
        this.range = range;
        this.messages = messages;
        this.text = new NumberText(messages);
        values.setConverter(new StringConverter<>() {
            @Override
            public String toString(Double value) {
                return value == null ? "" : text.write(value, NumberInput.this.range.decimals());
            }

            // a text the field does not take keeps the value, so the spinner leaves the text as typed
            @Override
            public Double fromString(String typed) {
                return read(typed) instanceof NumberText.Read.Number(var number) ? number : values.getValue();
            }
        });
        var start = Math.clamp(0, range.lowest(), range.highest());
        values.setValue(start);
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
        loaded = start;
    }

    /**
     * Shows a value and remembers it as the one not edited.
     *
     * @param value the value to show, as it is: one the engine refuses shows too
     */
    public void load(double value) {
        loaded = value;
        set(value);
    }

    /**
     * Shows a value as if the owner entered it, so it counts as changed when it differs from the loaded one.
     *
     * @param value the value to show, as it is
     */
    public void set(double value) {
        values.setValue(value);
        spinner.getEditor().setText(values.getConverter().toString(values.getValue()));
    }

    /**
     * Changes how the field shows and steps, such as for other units. The value shown is not changed.
     */
    void setRange(NumberRange range) {
        this.range = range;
    }

    /**
     * The value now, with what the owner typed but did not confirm yet; the field does not change, see
     * {@link #commitTyped()}.
     *
     * @return the typed number, also one the engine refuses; exactly the loaded one when the owner did not change
     *     it; NaN while the text is not a number the field takes, see {@link #problem()}
     */
    public double value() {
        return read(spinner.getEditor().getText()) instanceof NumberText.Read.Number(var number) ? number : Double.NaN;
    }

    @Override
    public Optional<String> problem() {
        return read(spinner.getEditor().getText()) instanceof NumberText.Read.Wrong(var problem)
                ? Optional.of(messages.text(problem)) : Optional.empty();
    }

    @Override
    public String outOfRange() {
        return range.limit().text(range.decimals(), messages);
    }

    /**
     * Confirms what the owner typed, as Enter does: the field then shows a typed number as it writes numbers. A text
     * that is not a number the field takes stays as typed. Not done while the owner types, as it rewrites the text.
     */
    @Override
    public void commitTyped() {
        var typed = spinner.getEditor().getText();
        if (read(typed) instanceof NumberText.Read.Number(var number)
                && !typed.equals(values.getConverter().toString(values.getValue()))) {
            set(number);
        }
    }

    /**
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

    /** A text that is not a number is a change: Save then checks it and says what is wrong. */
    @Override
    public boolean isChanged() {
        return Double.compare(value(), loaded) != 0;
    }

    @Override
    public void onChange(Runnable action) {
        spinner.valueProperty().addListener((_, _, _) -> action.run());
        spinner.getEditor().textProperty().addListener((_, _, _) -> action.run());
    }

    private NumberText.Read read(String typed) {
        return text.read(typed, values.getValue(), range.decimals(), range.whole());
    }

    /**
     * Holds any value it is given, where JavaFX's own factory brings each into its range, so a value the engine
     * allows is never cut to a range of the field's. Only the arrows stop, at the engine's limit.
     */
    private final class Values extends SpinnerValueFactory<Double> {

        @Override
        public void increment(int steps) {
            var from = getValue();
            if (from < range.highest()) {
                setValue(Math.min(stepped(from, steps), range.highest()));
            }
        }

        @Override
        public void decrement(int steps) {
            var from = getValue();
            if (from > range.lowest()) {
                setValue(Math.max(stepped(from, -steps), range.lowest()));
            }
        }

        /** In decimals, so 0.2 and a step of 0.05 make 0.25, not 0.25000000000000006. */
        private double stepped(double from, int steps) {
            return BigDecimal.valueOf(from)
                    .add(BigDecimal.valueOf(range.step()).multiply(BigDecimal.valueOf(steps))).doubleValue();
        }
    }
}
