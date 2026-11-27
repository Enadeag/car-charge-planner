package nl.enadeag.carchargeplanner.desktop.settings.field;

import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.desktop.text.TextKey;
import nl.enadeag.carchargeplanner.desktop.theme.Labels;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * One setting on a card: its name and its control side by side, a sentence that explains it below them, and room for
 * a problem with its value, which goes when the value is edited.
 */
public final class FieldRow extends VBox {

    private final Input input;
    private final Label hint = Labels.paragraph("", "note");
    private final Label problem = Labels.paragraph("", "field-problem");
    private final Label tag = new Label();

    /**
     * Makes the row.
     *
     * @param name the setting's name, in the owner's language; empty when the control says it itself, as a check box
     *     does
     * @param hint a sentence that explains the setting, in the owner's language; may be empty
     * @param input the control the setting is entered in
     */
    public FieldRow(String name, String hint, Input input) {
        this.input = input;
        var line = new HBox();
        line.getStyleClass().add("field-line");
        if (!name.isEmpty()) {
            var label = Labels.paragraph(name, "field-label");
            label.setLabelFor(input.focusable());
            label.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(label, Priority.ALWAYS);
            line.getChildren().add(label);
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
        input.onChange(this::clearProblem);
    }

    /**
     * The control the setting is entered in.
     *
     * @return the control this row was made with
     */
    public Input input() {
        return input;
    }

    /**
     * Replaces the sentence that explains the setting.
     *
     * @param text the new sentence, in the owner's language; empty to show none
     */
    public void setHint(String text) {
        hint.setText(text);
        hint.setVisible(!text.isEmpty());
        hint.setManaged(!text.isEmpty());
    }

    /**
     * Shows what is wrong with the value, below the explanation.
     *
     * @param text the problem, in the owner's language
     */
    public void showProblem(String text) {
        problem.setText(text);
        problem.setVisible(true);
        problem.setManaged(true);
    }

    /** Takes a problem away again. */
    public void clearProblem() {
        problem.setText("");
        problem.setVisible(false);
        problem.setManaged(false);
    }

    /**
     * Tags the value, such as a required setting, or takes the tag away. The tag stands before the control.
     *
     * @param text the tag, such as "Required" in the owner's language; empty to take it away
     */
    public void showTag(String text) {
        tag.setText(text);
        tag.setVisible(!text.isEmpty());
        tag.setManaged(!text.isEmpty());
    }

    /**
     * Whether the row shows a problem now.
     *
     * @return true from {@link #showProblem(String)} until the problem is taken away or the value is edited
     */
    public boolean hasProblem() {
        return problem.isVisible();
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
    public static FieldRow of(Messages messages, TextKey name, TextKey hint, Input input) {
        return new FieldRow(messages.text(name), messages.text(hint), input);
    }
}
