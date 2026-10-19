package nl.enadeag.carchargeplanner.desktop.dashboard;

import static nl.enadeag.carchargeplanner.desktop.dashboard.Texts.label;
import static nl.enadeag.carchargeplanner.desktop.dashboard.Texts.paragraph;

import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.desktop.text.TextKey;
import nl.enadeag.carchargeplanner.engine.away.AwayPeriod;
import java.util.Optional;
import java.util.function.Consumer;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * The card with the away periods the owner entered for specific days: those still to come, each with a Remove button,
 * and an Add button that enters one on the selected day. The weekly away periods are in Settings.
 */
final class OneOffAwayCard extends VBox {

    private final Messages messages;
    private final Runnable resized;
    private final Button add;
    private final VBox list = new VBox();
    private final AwayEditor editor;
    private Optional<Consumer<AwayPeriod>> added = Optional.empty();
    private Optional<Consumer<AwayPeriod>> removed = Optional.empty();
    private Optional<DayModel> day = Optional.empty();
    private AwayModel model;

    /**
     * Makes the card, empty until {@link #show(DayModel, AwayModel)}.
     *
     * @param resized called when the entry opens, closes or changes its height, so the popup or window can fit the
     *     card
     * @param messages the texts, in the owner's language
     */
    OneOffAwayCard(Runnable resized, Messages messages) {
        this.messages = messages;
        this.resized = resized;
        add = new Button(messages.text(TextKey.ONE_OFF_ADD));
        add.getStyleClass().addAll("flat", "small");
        add.setMinWidth(USE_PREF_SIZE);
        add.setOnAction(event -> {
            edit(true);
            resized.run();
        });
        editor = new AwayEditor(period -> added.ifPresent(handler -> handler.accept(period)), this::closeEntry, resized,
                messages);
        var spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        var header = new HBox(label(messages.text(TextKey.ONE_OFF_HEADING), "card-label"), spacer, add);
        header.getStyleClass().add("chart-header");
        list.getStyleClass().add("away-list");
        getChildren().setAll(header, list, editor);
        getStyleClass().addAll("card", "away-card");
        edit(false);
    }

    /**
     * Shows the coming away periods, with Add for a day. An open entry stays open while the same day shows, and
     * closes on another day.
     *
     * @param selected the selected day, on which Add enters a period
     * @param away the coming away periods and the time of the plan
     */
    void show(DayModel selected, AwayModel away) {
        boolean sameDay = day.map(DayModel::date).filter(selected.date()::equals).isPresent();
        day = Optional.of(selected);
        model = away;
        var hint = messages.text(TextKey.ONE_OFF_ADD_HINT, selected.title());
        add.setTooltip(new Tooltip(hint));
        add.setAccessibleHelp(hint);
        list.getChildren().clear();
        if (away.coming().isEmpty()) {
            list.getChildren().add(paragraph(messages.text(TextKey.ONE_OFF_NONE), "note"));
        }
        away.coming().forEach(entry -> list.getChildren().add(row(entry)));
        if (!sameDay) {
            edit(false);
        }
    }

    /**
     * Lets the owner add and remove away periods from now on; until then the card cannot be used.
     *
     * @param addedHandler gets a period the owner entered; called on the JavaFX thread
     * @param removedHandler gets a period the owner removed, as the model gave it; called on the JavaFX thread
     */
    void onChange(Consumer<AwayPeriod> addedHandler, Consumer<AwayPeriod> removedHandler) {
        added = Optional.of(addedHandler);
        removed = Optional.of(removedHandler);
    }

    /**
     * Whether the owner can add and remove periods here, so the card is worth showing.
     *
     * @return true once {@link #onChange} gave somewhere to send them
     */
    boolean usable() {
        return added.isPresent() && removed.isPresent();
    }

    /**
     * The entry, for the tests.
     *
     * @return the card's entry; shown only while the owner enters a period
     */
    AwayEditor editor() {
        return editor;
    }

    private HBox row(AwayModel.Entry entry) {
        var text = paragraph(entry.text(), "away-item");
        HBox.setHgrow(text, Priority.ALWAYS);
        text.setMaxWidth(Double.MAX_VALUE);
        var remove = new Button(messages.text(TextKey.ONE_OFF_REMOVE));
        remove.getStyleClass().addAll("flat", "small");
        remove.setMinWidth(USE_PREF_SIZE);
        remove.setAccessibleText(entry.removeSpoken());
        remove.setOnAction(event -> removed.ifPresent(handler -> handler.accept(entry.period())));
        var row = new HBox(text, remove);
        row.getStyleClass().add("away-row");
        return row;
    }

    /** After a save or a cancel the focus goes back to Add, where the keyboard was before the entry opened. */
    private void closeEntry() {
        edit(false);
        add.requestFocus();
        resized.run();
    }

    private void edit(boolean open) {
        boolean opens = open && day.isPresent() && usable();
        editor.setVisible(opens);
        editor.setManaged(opens);
        add.setVisible(!opens);
        add.setManaged(!opens);
        if (opens) {
            editor.start(day.get().date(), model.zone(), model.now());
        }
    }
}
