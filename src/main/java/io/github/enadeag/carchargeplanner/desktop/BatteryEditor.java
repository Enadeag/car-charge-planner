package io.github.enadeag.carchargeplanner.desktop;

import java.util.function.IntConsumer;
import javafx.scene.control.Button;
import javafx.scene.control.Spinner;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;

/** Where the owner enters the battery level the car shows: a number from 0 to 100 with Save and Cancel. */
final class BatteryEditor extends HBox {

    private final Spinner<Integer> level = new Spinner<>(0, 100, 50);
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
        this.saved = saved;
        this.closed = closed;
        level.setEditable(true);
        level.setAccessibleText(messages.text(TextKey.BATTERY_LEVEL_FIELD));
        level.getStyleClass().add("battery-level-field");
        var percent = Texts.label("%", "fact");
        var save = new Button(messages.text(TextKey.BATTERY_SAVE));
        save.getStyleClass().add("accent");
        save.setOnAction(event -> save());
        var cancel = new Button(messages.text(TextKey.BATTERY_CANCEL));
        cancel.getStyleClass().add("flat");
        cancel.setOnAction(event -> closed.run());
        level.getEditor().setOnAction(event -> save());
        setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                closed.run();
                event.consume();
            }
        });
        getChildren().setAll(level, percent, save, cancel);
        getStyleClass().add("battery-editor");
    }

    /**
     * Opens the entry at a level and puts the focus in it.
     *
     * @param pct the level to start from, in percent; rounded to a whole percent
     */
    void start(double pct) {
        level.getValueFactory().setValue((int) Math.round(Math.clamp(pct, 0, 100)));
        level.requestFocus();
        level.getEditor().selectAll();
    }

    /**
     * Saves what the owner typed or chose; a typed text that is not a number keeps the level shown before.
     */
    void save() {
        try {
            level.commitValue();
        } catch (NumberFormatException e) {
            level.getEditor().setText(String.valueOf(level.getValue()));
        }
        saved.accept(level.getValue());
        closed.run();
    }

    /**
     * The field, for the tests.
     *
     * @return the spinner the level is entered in
     */
    Spinner<Integer> field() {
        return level;
    }
}
