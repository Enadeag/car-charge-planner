package io.github.enadeag.carchargeplanner.desktop.settings;

import io.github.enadeag.carchargeplanner.desktop.Messages;
import io.github.enadeag.carchargeplanner.desktop.TextKey;
import io.github.enadeag.carchargeplanner.engine.config.InvalidSetting;
import io.github.enadeag.carchargeplanner.engine.config.PanelArray;
import io.github.enadeag.carchargeplanner.engine.config.Setting;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;

/** One panel array: its name, size, tilt, direction and losses, with typical values on request. */
final class PanelArrayCard {

    private final Messages messages;
    private final TextInput name = new TextInput();
    private final NumberField size;
    private final NumberField tilt;
    private final NumberField direction;
    private final NumberField losses;
    private final ProblemRows problems;
    private final TypicalMarks typical;
    private final Button remove;
    private final Card card;

    /**
     * Makes the card, empty until {@link #load(PanelArray, Set)}.
     *
     * @param remove what the card's Remove button does: take this card's array away; given this card
     * @param messages the texts, in the owner's language
     */
    PanelArrayCard(Consumer<PanelArrayCard> remove, Messages messages) {
        this.messages = messages;
        size = new NumberField(new NumberField.Range(0.1, 100, 0.1, 2), "kWp", messages);
        tilt = new NumberField(new NumberField.Range(0, 90, 1, 0), "°", messages);
        direction = new NumberField(new NumberField.Range(-180, 180, 5, 0), "°", messages);
        losses = new NumberField(new NumberField.Range(0, 99, 1, 0), "%", messages);
        problems = new ProblemRows(messages);
        typical = new TypicalMarks(messages.text(TextKey.TYPICAL_TAG));
        var useTypical = new Button(messages.text(TextKey.USE_TYPICAL));
        useTypical.setOnAction(event -> useTypical());
        this.remove = new Button(messages.text(TextKey.PANELS_REMOVE));
        this.remove.setOnAction(event -> remove.accept(this));
        var buttons = new HBox(useTypical, this.remove);
        buttons.getStyleClass().add("card-buttons");
        card = new Card("", messages.text(TextKey.CARD_PANEL_ARRAY_HINT),
                FieldRow.of(messages, TextKey.FIELD_PANEL_NAME, TextKey.FIELD_PANEL_NAME_HINT, name),
                row(Setting.PANEL_SIZE, TextKey.FIELD_PANEL_SIZE, TextKey.FIELD_PANEL_SIZE_HINT, size),
                row(Setting.PANEL_TILT, TextKey.FIELD_PANEL_TILT, TextKey.FIELD_PANEL_TILT_HINT, tilt),
                row(Setting.PANEL_DIRECTION, TextKey.FIELD_PANEL_DIRECTION, TextKey.FIELD_PANEL_DIRECTION_HINT,
                        direction),
                row(Setting.PANEL_LOSSES, TextKey.FIELD_PANEL_LOSSES, TextKey.FIELD_PANEL_LOSSES_HINT, losses),
                buttons);
    }

    /**
     * Shows a stored array, as the values not edited.
     *
     * @param array the array
     * @param typicalValues which of its values are typical values
     */
    void load(PanelArray array, Set<Setting> typicalValues) {
        typical.load(typicalValues, () -> {
            name.load(array.name());
            size.load(array.kwp());
            tilt.load(array.tiltDeg());
            direction.load(array.azimuthDeg());
            losses.load(array.systemLosses() * 100);
        });
        problems.clear();
    }

    /**
     * Gives the card its place among the arrays, for its title; with one array there is nothing to remove.
     *
     * @param index the array's place, from 0
     * @param count how many arrays there are, at least 1
     */
    void number(int index, int count) {
        card.setTitle(messages.text(TextKey.CARD_PANEL_ARRAY, index + 1));
        remove.setVisible(count > 1);
        remove.setManaged(count > 1);
    }

    /**
     * The array as the card shows it, after its record checked it; a refused value shows its problem.
     *
     * @return the array, or empty when a value is refused
     */
    Optional<PanelArray> value() {
        problems.clear();
        try {
            return Optional.of(new PanelArray(name.value(), size.value(), tilt.value(), direction.value(),
                    losses.value() / 100));
        } catch (InvalidSetting e) {
            problems.show(e);
            return Optional.empty();
        }
    }

    /**
     * The values that are typical values now.
     *
     * @return the tagged settings
     */
    Set<Setting> typical() {
        return typical.marked();
    }

    /**
     * Whether the owner changed something since the array was loaded.
     *
     * @return true when saving would change the array or its typical values
     */
    boolean isChanged() {
        return typical.isChanged() || inputs().stream().anyMatch(Input::isChanged);
    }

    /**
     * Calls an action whenever a value changes.
     *
     * @param action what to run, on the JavaFX thread
     */
    void onChange(Runnable action) {
        inputs().forEach(input -> input.onChange(action));
    }

    /**
     * The card itself.
     *
     * @return the same card every time
     */
    Card view() {
        return card;
    }

    private void useTypical() {
        var common = PanelArray.TYPICAL;
        typical.useTypical(() -> {
            size.set(common.kwp());
            tilt.set(common.tiltDeg());
            direction.set(common.azimuthDeg());
            losses.set(common.systemLosses() * 100);
        });
    }

    private List<Input> inputs() {
        return List.of(name, size, tilt, direction, losses);
    }

    private FieldRow row(Setting setting, TextKey label, TextKey hint, Input input) {
        var row = problems.add(setting, FieldRow.of(messages, label, hint, input));
        typical.follow(setting, row, input);
        return row;
    }
}
