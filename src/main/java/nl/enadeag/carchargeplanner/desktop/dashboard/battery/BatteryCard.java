package nl.enadeag.carchargeplanner.desktop.dashboard.battery;

import static nl.enadeag.carchargeplanner.desktop.theme.Labels.line;

import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.desktop.text.TextKey;
import java.util.function.IntConsumer;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * The card with the selected day's battery: its heading, the levels in figures and the bar. On today's card the owner
 * can enter the level the car shows, with the Update button or by tapping the bar.
 */
public final class BatteryCard extends VBox {

    private final BatteryBar bar;
    private final Label levels = line("", "battery-levels");
    private final Button update;
    private final BatteryEditor editor;
    private IntConsumer entered = _ -> { };
    /** True once {@link #onEntered(IntConsumer)} gave somewhere to send the level. */
    private boolean acceptsLevel;
    private BatteryModel model;

    /**
     * Makes the card, empty until {@link #show(BatteryModel)}.
     *
     * @param reducedMotion true while the system asks for reduced motion; the bar then changes without sliding
     * @param messages the texts, in the owner's language
     */
    public BatteryCard(ObservableValue<Boolean> reducedMotion, Messages messages) {
        bar = new BatteryBar(reducedMotion);
        update = new Button(messages.text(TextKey.BATTERY_UPDATE));
        editor = new BatteryEditor(pct -> entered.accept(pct), this::closeEntry,
                messages);
        update.getStyleClass().addAll("flat", "small");
        var hint = messages.text(TextKey.BATTERY_UPDATE_HINT);
        update.setTooltip(new Tooltip(hint));
        update.setAccessibleHelp(hint);
        update.setOnAction(_ -> openEditor());
        bar.setOnMouseClicked(_ -> openEditor());
        bar.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER || event.getCode() == KeyCode.SPACE) {
                openEditor();
                event.consume();
            }
        });
        var spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        var header = new HBox(line(messages.text(TextKey.BATTERY_HEADING), "card-label"), spacer, levels, update);
        header.getStyleClass().add("chart-header");
        getChildren().setAll(header, bar, editor);
        getStyleClass().addAll("card", "battery-card");
        closeEditor();
    }

    /**
     * Shows a day's battery. The entry stays open while today shows, and closes on another day.
     *
     * @param day the day's levels; never null
     */
    public void show(BatteryModel day) {
        model = day;
        levels.setText(day.levels());
        bar.show(day);
        refreshUpdateButton();
        if (!canEnterLevel()) {
            closeEditor();
        }
    }

    /**
     * Lets the owner enter today's level from now on.
     *
     * @param handler gets the level the owner saved, in percent from 0 to 100, on the JavaFX thread
     */
    public void onEntered(IntConsumer handler) {
        entered = handler;
        acceptsLevel = true;
        refreshUpdateButton();
    }

    /**
     * Whether the owner can enter today's level here.
     *
     * @return true once {@link #onEntered(IntConsumer)} gave somewhere to send the level
     */
    public boolean acceptsLevel() {
        return acceptsLevel;
    }

    /**
     * Opens the entry of the level, as Update does, with the focus in it; nothing happens while the card shows a day
     * other than today or cannot send the level anywhere.
     */
    public void enterLevel() {
        if (canEnterLevel()) {
            openEditor();
        }
    }

    /**
     * The bar, for the tests.
     *
     * @return the card's bar
     */
    BatteryBar bar() {
        return bar;
    }

    /**
     * The entry, for the tests.
     *
     * @return the card's entry; shown only while the owner enters a level
     */
    BatteryEditor editor() {
        return editor;
    }

    private boolean canEnterLevel() {
        return model != null && model.editable() && acceptsLevel;
    }

    private void refreshUpdateButton() {
        boolean can = canEnterLevel();
        update.setVisible(can && !editor.isVisible());
        update.setManaged(update.isVisible());
        bar.setAccessibleHelp(can ? update.getAccessibleHelp() : null);
    }

    /** After a save or a cancel the focus goes back to Update, where the keyboard was before the entry opened. */
    private void closeEntry() {
        closeEditor();
        update.requestFocus();
    }

    /** Opens the entry at the day's level when today's level can be entered; otherwise it stays closed. */
    private void openEditor() {
        if (canEnterLevel()) {
            showEditor(true);
            editor.prepare(model.levelPct());
        } else {
            closeEditor();
        }
    }

    private void closeEditor() {
        showEditor(false);
    }

    private void showEditor(boolean shown) {
        editor.setVisible(shown);
        editor.setManaged(shown);
        refreshUpdateButton();
    }
}
