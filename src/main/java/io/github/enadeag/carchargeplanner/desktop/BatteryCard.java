package io.github.enadeag.carchargeplanner.desktop;

import static io.github.enadeag.carchargeplanner.desktop.Texts.label;

import java.util.Optional;
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
final class BatteryCard extends VBox {

    private final BatteryBar bar;
    private final Label levels = label("", "battery-levels");
    private final Button update;
    private final BatteryEditor editor;
    private Optional<IntConsumer> entered = Optional.empty();
    private BatteryModel model;

    /**
     * Makes the card, empty until {@link #show(BatteryModel)}.
     *
     * @param reducedMotion true while the system asks for reduced motion; the bar then changes without sliding
     * @param messages the texts, in the owner's language
     */
    BatteryCard(ObservableValue<Boolean> reducedMotion, Messages messages) {
        bar = new BatteryBar(reducedMotion);
        update = new Button(messages.text(TextKey.BATTERY_UPDATE));
        editor = new BatteryEditor(pct -> entered.ifPresent(handler -> handler.accept(pct)), () -> edit(false), messages);
        update.getStyleClass().addAll("flat", "small");
        var hint = messages.text(TextKey.BATTERY_UPDATE_HINT);
        update.setTooltip(new Tooltip(hint));
        update.setAccessibleHelp(hint);
        update.setOnAction(event -> edit(true));
        bar.setOnMouseClicked(event -> edit(true));
        bar.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER || event.getCode() == KeyCode.SPACE) {
                edit(true);
                event.consume();
            }
        });
        var spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        var header = new HBox(label(messages.text(TextKey.BATTERY_HEADING), "card-label"), spacer, levels, update);
        header.getStyleClass().add("chart-header");
        getChildren().setAll(header, bar, editor);
        getStyleClass().addAll("card", "battery-card");
        edit(false);
    }

    /**
     * Shows a day's battery. The entry stays open while today shows, and closes on another day.
     *
     * @param day the day's levels; never null
     */
    void show(BatteryModel day) {
        model = day;
        levels.setText(day.levels());
        bar.show(day);
        showUpdate();
        if (!canEdit()) {
            edit(false);
        }
    }

    /**
     * Lets the owner enter today's level from now on.
     *
     * @param handler gets the level the owner saved, in percent from 0 to 100, on the JavaFX thread
     */
    void onEntered(IntConsumer handler) {
        entered = Optional.of(handler);
        showUpdate();
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

    private boolean canEdit() {
        return model != null && model.editable() && entered.isPresent();
    }

    private void showUpdate() {
        boolean can = canEdit();
        update.setVisible(can && !editor.isVisible());
        update.setManaged(update.isVisible());
    }

    private void edit(boolean open) {
        boolean opens = open && canEdit();
        editor.setVisible(opens);
        editor.setManaged(opens);
        showUpdate();
        if (opens) {
            editor.start(model.levelPct());
        }
    }
}
