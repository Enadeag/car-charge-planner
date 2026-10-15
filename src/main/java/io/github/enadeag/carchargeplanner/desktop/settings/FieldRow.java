package io.github.enadeag.carchargeplanner.desktop.settings;

import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * One setting on a card: its name and its control side by side, a sentence that explains it below them, and room for
 * a problem with its value. A screen reader reads the name with the control, and the explanation and problem as its
 * help.
 */
final class FieldRow extends VBox {

    private final Input input;
    private final Label hint = paragraph("", "note");
    private final Label problem = paragraph("", "field-problem");

    /**
     * Makes the row.
     *
     * @param name the setting's name, in the owner's language; empty when the control says it itself, as a check box
     *     does
     * @param hint a sentence that explains the setting, in the owner's language; may be empty
     * @param input the control the setting is entered in
     */
    FieldRow(String name, String hint, Input input) {
        this.input = input;
        var line = new HBox();
        line.getStyleClass().add("field-line");
        if (!name.isEmpty()) {
            var label = paragraph(name, "field-label");
            label.setLabelFor(input.focusable());
            label.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(label, Priority.ALWAYS);
            line.getChildren().add(label);
            input.focusable().setAccessibleText(name);
        }
        line.getChildren().add(input.view());
        problem.setVisible(false);
        problem.setManaged(false);
        getChildren().addAll(line, this.hint, problem);
        setHint(hint);
        getStyleClass().add("field-row");
    }

    /**
     * Replaces the sentence that explains the setting.
     *
     * @param text the new sentence, in the owner's language; empty to show none
     */
    void setHint(String text) {
        hint.setText(text);
        hint.setVisible(!text.isEmpty());
        hint.setManaged(!text.isEmpty());
        input.focusable().setAccessibleHelp(help());
    }

    /**
     * Shows what is wrong with the value, below the explanation.
     *
     * @param text the problem, in the owner's language
     */
    void showProblem(String text) {
        problem.setText(text);
        problem.setVisible(true);
        problem.setManaged(true);
        input.focusable().setAccessibleHelp(help());
    }

    /** Takes a problem away again. */
    void clearProblem() {
        problem.setText("");
        problem.setVisible(false);
        problem.setManaged(false);
        input.focusable().setAccessibleHelp(help());
    }

    /**
     * Whether a problem shows.
     *
     * @return true while the row shows a problem
     */
    boolean hasProblem() {
        return problem.isVisible();
    }

    /** The problem first, as it is what the owner has to act on. */
    private String help() {
        return (problem.getText() + " " + hint.getText()).strip();
    }

    /**
     * A label that wraps onto more lines rather than cutting its text short.
     *
     * @param text the text; may be empty
     * @param styleClass the label's class in the stylesheet
     * @return a new label
     */
    static Label paragraph(String text, String styleClass) {
        var label = new Label(text);
        label.getStyleClass().add(styleClass);
        label.setWrapText(true);
        label.setMinHeight(Label.USE_PREF_SIZE);
        return label;
    }
}
