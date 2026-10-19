package nl.enadeag.carchargeplanner.desktop.dashboard.battery;

import nl.enadeag.carchargeplanner.desktop.theme.Labels;
import nl.enadeag.carchargeplanner.desktop.theme.ScreenReader;
import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.desktop.text.TextKey;
import java.util.function.IntConsumer;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * Where the owner enters the battery level the car shows: a number from 0 to 100 with Save and Cancel. A text that is
 * not a whole number is not saved: the entry says why, stays open and keeps the focus in the field, where a screen
 * reader reads the reason.
 */
final class BatteryEditor extends VBox {

    private final Spinner<Integer> level = new Spinner<>(0, 100, 50);
    private final Label problem = Labels.paragraph("", "field-problem");
    private final Messages messages;
    private final IntConsumer saved;
    private final Runnable closed;

    /**
     * Makes the entry.
     *
     * @param saved gets the level the owner saved, in percent from 0 to 100; called on the JavaFX thread
     * @param closed called when the entry should close, after a save or a cancel
     * @param messages the texts, in the owner's language
     */
    BatteryEditor(IntConsumer saved, Runnable closed, Messages messages) {
        this.messages = messages;
        this.saved = saved;
        this.closed = closed;
        level.setEditable(true);
        level.setAccessibleText(messages.text(TextKey.BATTERY_LEVEL_FIELD));
        level.getStyleClass().add("battery-level-field");
        var percent = Labels.line("%", "fact");
        var save = new Button(messages.text(TextKey.BATTERY_SAVE));
        save.getStyleClass().add("accent");
        save.setOnAction(_ -> save());
        var cancel = new Button(messages.text(TextKey.BATTERY_CANCEL));
        cancel.getStyleClass().add("flat");
        cancel.setOnAction(_ -> closed.run());
        // the away entry below can be open as well, with its own Save and Cancel
        var heading = messages.text(TextKey.BATTERY_HEADING);
        save.setAccessibleText(messages.text(TextKey.IN_GROUP, save.getText(), heading));
        cancel.setAccessibleText(messages.text(TextKey.IN_GROUP, cancel.getText(), heading));
        level.getEditor().setOnAction(_ -> save());
        setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                closed.run();
                event.consume();
            }
        });
        var line = new HBox(level, percent, save, cancel);
        line.getStyleClass().add("battery-editor-line");
        getChildren().setAll(line, problem);
        getStyleClass().add("battery-editor");
        showProblem("");
    }

    /**
     * Fills the entry with a level and puts the focus in it; the card shows the entry.
     *
     * @param pct the level to start from, in percent; rounded to a whole percent
     */
    void prepare(double pct) {
        level.getValueFactory().setValue((int) Math.round(Math.clamp(pct, 0, 100)));
        showProblem("");
        level.requestFocus();
        level.getEditor().selectAll();
    }

    /**
     * Saves what the owner typed or chose, or says why it cannot be saved. A whole number outside 0 to 100 is saved
     * as the nearest of the two.
     */
    void save() {
        Integer typed;
        try {
            typed = level.getValueFactory().getConverter().fromString(level.getEditor().getText());
        } catch (NumberFormatException _) {
            typed = null;
        }
        if (typed == null) {
            showProblem(messages.text(TextKey.BATTERY_NOT_A_NUMBER));
            ScreenReader.focus(level);
            return;
        }
        level.getValueFactory().setValue(typed);
        showProblem("");
        saved.accept(level.getValue());
        closed.run();
    }

    /**
     * Why the typed level was not saved.
     *
     * @return the problem shown, or empty while there is none
     */
    String problem() {
        return problem.isVisible() ? problem.getText() : "";
    }

    /**
     * The field, for the tests.
     *
     * @return the spinner the level is entered in
     */
    Spinner<Integer> field() {
        return level;
    }

    private void showProblem(String text) {
        problem.setText(text);
        problem.setVisible(!text.isEmpty());
        problem.setManaged(!text.isEmpty());
        level.setAccessibleHelp(text);
    }
}
