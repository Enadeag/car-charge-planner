package io.github.enadeag.carchargeplanner.desktop.settings;

import io.github.enadeag.carchargeplanner.desktop.Messages;
import io.github.enadeag.carchargeplanner.desktop.TextKey;
import java.util.Optional;
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
    private final String name;
    private final Optional<Label> label;
    private final Label hint = paragraph("", "note");
    private final Label problem = paragraph("", "field-problem");
    private final Label tag = new Label();

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
        this.name = name;
        var line = new HBox();
        line.getStyleClass().add("field-line");
        if (name.isEmpty()) {
            label = Optional.empty();
        } else {
            var shown = paragraph(name, "field-label");
            shown.setLabelFor(input.focusable());
            shown.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(shown, Priority.ALWAYS);
            line.getChildren().add(shown);
            input.focusable().setAccessibleText(name);
            label = Optional.of(shown);
        }
        tag.getStyleClass().add("field-tag");
        tag.setMinWidth(USE_PREF_SIZE);
        tag.setVisible(false);
        tag.setManaged(false);
        line.getChildren().addAll(tag, input.view());
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
     * Tags the value, such as a typical value or a required setting, or takes the tag away. The tag stands before the
     * control, and a screen reader reads it with the explanation.
     *
     * @param text the tag, such as "Typical value" in the owner's language; empty to take it away
     */
    void showTag(String text) {
        tag.setText(text);
        tag.setVisible(!text.isEmpty());
        tag.setManaged(!text.isEmpty());
        input.focusable().setAccessibleHelp(help());
    }

    /**
     * Gives the field a longer name for a screen reader, where a card with the same fields shows more than once, such
     * as a second panel array. The label on screen keeps the field's own name.
     *
     * @param spoken the name to read, such as "Size (Panel array 2)"; the field's own name to undo it
     */
    void setSpokenName(String spoken) {
        // a text field or combo box is named after its label, a spinner or check box after its own text
        label.ifPresent(shown -> shown.setAccessibleText(spoken.equals(name) ? null : spoken));
        input.focusable().setAccessibleText(spoken);
    }

    /**
     * The field's own name, as its label shows it.
     *
     * @return the name; empty when the control says it itself, as a check box does
     */
    String name() {
        return name;
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
        return (problem.getText() + " " + tag.getText() + " " + hint.getText()).strip().replaceAll(" +", " ");
    }

    /**
     * A row with its texts from the bundles.
     *
     * @param messages the texts, in the owner's language
     * @param name the key of the setting's name
     * @param hint the key of the sentence that explains it
     * @param input the control the setting is entered in
     * @return a new row
     */
    static FieldRow of(Messages messages, TextKey name, TextKey hint, Input input) {
        return new FieldRow(messages.text(name), messages.text(hint), input);
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
