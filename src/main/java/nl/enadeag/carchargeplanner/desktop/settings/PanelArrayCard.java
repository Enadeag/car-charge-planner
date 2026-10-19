package nl.enadeag.carchargeplanner.desktop.settings;

import nl.enadeag.carchargeplanner.desktop.settings.form.NumberRange;
import nl.enadeag.carchargeplanner.desktop.settings.form.PanelArrayEntry;
import nl.enadeag.carchargeplanner.desktop.settings.form.TypicalMarks;
import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.desktop.text.TextKey;
import nl.enadeag.carchargeplanner.engine.config.PanelArray;
import nl.enadeag.carchargeplanner.engine.setting.InvalidSetting;
import nl.enadeag.carchargeplanner.engine.setting.Setting;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import javafx.scene.control.Button;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Region;

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
    private final Button useTypical;
    private final Button remove;
    private final List<FieldRow> rows = new ArrayList<>();
    private final Card card;

    /**
     * Makes the card, empty until {@link #load(PanelArray, Set)}.
     *
     * @param remove what the card's Remove button does: take this card's array away; given this card
     * @param messages the texts, in the owner's language
     */
    PanelArrayCard(Consumer<PanelArrayCard> remove, Messages messages) {
        this.messages = messages;
        size = new NumberField(new NumberRange(0.1, 100, 0.1, 2), "kWp", messages);
        tilt = new NumberField(new NumberRange(0, 90, 1, 0), "°", messages);
        direction = new NumberField(new NumberRange(-180, 180, 5, 0), "°", messages);
        losses = new NumberField(new NumberRange(0, 99, 1, 0), "%", messages);
        problems = new ProblemRows(messages);
        typical = new TypicalMarks(messages.text(TextKey.TYPICAL_TAG));
        this.useTypical = new Button(messages.text(TextKey.USE_TYPICAL));
        this.useTypical.setOnAction(_ -> useTypical());
        this.remove = new Button(messages.text(TextKey.PANELS_REMOVE));
        this.remove.setOnAction(_ -> remove.accept(this));
        // with larger text the two buttons go below each other rather than being cut short
        var buttons = new FlowPane(this.useTypical, this.remove);
        this.useTypical.setMinWidth(Region.USE_PREF_SIZE);
        this.remove.setMinWidth(Region.USE_PREF_SIZE);
        buttons.getStyleClass().add("card-buttons");
        card = new Card("", messages.text(TextKey.CARD_PANEL_ARRAY_HINT),
                listed(FieldRow.of(messages, TextKey.FIELD_PANEL_NAME, TextKey.FIELD_PANEL_NAME_HINT, name)),
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
     * @param array the stored array whose name, size, tilt, direction and losses the fields show
     * @param typicalValues which of its values are typical values
     */
    void load(PanelArray array, Set<Setting> typicalValues) {
        var shown = PanelArrayEntry.of(array);
        typical.load(typicalValues, () -> {
            name.load(shown.name());
            size.load(shown.kwp());
            tilt.load(shown.tiltDeg());
            direction.load(shown.azimuthDeg());
            losses.load(shown.lossesPct());
        });
        problems.clear();
    }

    /**
     * Gives the card its place among the arrays, for its title; with one array there is nothing to remove. With more
     * than one, a screen reader reads each field and button with the array's title, so the arrays' twins differ.
     *
     * @param index the array's place, from 0
     * @param count how many arrays there are, at least 1
     */
    void number(int index, int count) {
        var title = messages.text(TextKey.CARD_PANEL_ARRAY, index + 1);
        card.setTitle(title);
        remove.setVisible(count > 1);
        remove.setManaged(count > 1);
        // with more than one array, a screen reader tells their fields and buttons apart by the array's number
        for (var row : rows) {
            row.setSpokenName(count > 1 ? messages.text(TextKey.IN_GROUP, row.name(), title) : row.name());
        }
        for (var button : List.of(useTypical, remove)) {
            button.setAccessibleText(count > 1 ? messages.text(TextKey.IN_GROUP, button.getText(), title) : null);
        }
    }

    /**
     * The array as the card shows it, after its record checked it; a refused value shows its problem.
     *
     * @return the array, or empty when a value is refused
     */
    Optional<PanelArray> value() {
        problems.clear();
        try {
            return Optional.of(new PanelArrayEntry(name.value(), size.value(), tilt.value(), direction.value(),
                    losses.value()).panelArray());
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
        var common = PanelArrayEntry.of(PanelArray.TYPICAL);
        typical.useTypical(() -> {
            size.set(common.kwp());
            tilt.set(common.tiltDeg());
            direction.set(common.azimuthDeg());
            losses.set(common.lossesPct());
        });
    }

    private List<Input> inputs() {
        return List.of(name, size, tilt, direction, losses);
    }

    private FieldRow listed(FieldRow row) {
        rows.add(row);
        return row;
    }

    private FieldRow row(Setting setting, TextKey label, TextKey hint, Input input) {
        var row = listed(problems.add(setting, FieldRow.of(messages, label, hint, input)));
        typical.follow(setting, row::showTag);
        input.onChange(() -> typical.edited(setting));
        return row;
    }
}
