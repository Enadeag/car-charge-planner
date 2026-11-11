package nl.enadeag.carchargeplanner.desktop.settings.field;

import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;

/**
 * A whole number the owner types, which may stay empty, such as a number of panels or a year. The text is given back
 * as typed, so the form can tell an empty field from a wrong one; while it is empty the field may show a value in
 * grey, such as an estimate the app uses in its place.
 */
public final class WholeNumberInput implements Input {

    private final TextField field = new TextField();
    private final HBox view;
    private String loaded = "";

    /**
     * Makes the field, empty until {@link #load(String)}.
     *
     * @param unit the unit after the field, such as "Wp"; empty for none
     */
    public WholeNumberInput(String unit) {
        field.getStyleClass().addAll("number-field", "whole-number-field");
        view = new HBox(field);
        if (!unit.isEmpty()) {
            var label = new Label(unit);
            label.getStyleClass().add("field-unit");
            view.getChildren().add(label);
        }
        view.getStyleClass().add("number-input");
    }

    /**
     * Shows a text and remembers it as the one not edited.
     *
     * @param text the number as digits, such as "12"; empty for none
     */
    public void load(String text) {
        loaded = text;
        field.setText(text);
    }

    /**
     * Shows a value in grey while the field is empty, or none.
     *
     * @param text such as "360"; empty to show nothing
     */
    public void showWhileEmpty(String text) {
        field.setPromptText(text);
    }

    /**
     * The text now.
     *
     * @return what the field holds, without spaces around it; empty when nothing is typed
     */
    public String value() {
        return field.getText().strip();
    }

    @Override
    public Node view() {
        return view;
    }

    @Override
    public Node focusable() {
        return field;
    }

    @Override
    public boolean isChanged() {
        return !value().equals(loaded);
    }

    @Override
    public void onChange(Runnable action) {
        field.textProperty().addListener((_, _, _) -> action.run());
    }
}
