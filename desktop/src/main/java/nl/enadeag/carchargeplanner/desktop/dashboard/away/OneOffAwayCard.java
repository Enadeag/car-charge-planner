package nl.enadeag.carchargeplanner.desktop.dashboard.away;

import static nl.enadeag.carchargeplanner.desktop.theme.Labels.line;
import static nl.enadeag.carchargeplanner.desktop.theme.Labels.paragraph;

import nl.enadeag.carchargeplanner.desktop.dashboard.day.DayModel;
import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.desktop.text.TextKey;
import nl.enadeag.carchargeplanner.engine.away.OneOffAwayPeriod;
import java.time.InstantSource;
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
public final class OneOffAwayCard extends VBox {

    private final Messages messages;
    private final Runnable resized;
    private final Button add;
    private final VBox list = new VBox();
    private final OneOffAwayEditor editor;
    private Consumer<OneOffAwayPeriod> added = _ -> { };
    private Consumer<OneOffAwayPeriod> removed = _ -> { };
    /** Tells the time when the owner saves a period; null until {@link #onChange} gives it. */
    private InstantSource clock;
    /** True once {@link #onChange} gave somewhere to send the periods. */
    private boolean acceptsChanges;
    /** The selected day; null until one shows. */
    private DayModel day;
    private OneOffAwayModel model;

    /**
     * Makes the card, empty until {@link #show(DayModel, OneOffAwayModel)}.
     *
     * @param resized called when the entry opens, closes or changes its height, so the popup or window can fit the
     *     card
     * @param messages the texts, in the owner's language
     */
    public OneOffAwayCard(Runnable resized, Messages messages) {
        this.messages = messages;
        this.resized = resized;
        add = new Button(messages.text(TextKey.ONE_OFF_ADD));
        add.getStyleClass().addAll("flat", "small");
        add.setMinWidth(USE_PREF_SIZE);
        add.setOnAction(_ -> {
            openEditor();
            resized.run();
        });
        editor = new OneOffAwayEditor(period -> added.accept(period), this::closeEntry, resized,
                messages);
        var spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        var header = new HBox(line(messages.text(TextKey.ONE_OFF_HEADING), "card-label"), spacer, add);
        header.getStyleClass().add("chart-header");
        list.getStyleClass().add("away-list");
        getChildren().setAll(header, list, editor);
        getStyleClass().addAll("card", "away-card");
        closeEditor();
    }

    /**
     * Shows the coming away periods, with Add for a day. An open entry stays open while the same day shows, and
     * closes on another day.
     *
     * @param selected the selected day, on which Add enters a period
     * @param away the coming away periods and the time of the plan
     */
    public void show(DayModel selected, OneOffAwayModel away) {
        boolean sameDay = day != null && day.date().equals(selected.date());
        day = selected;
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
            closeEditor();
        }
    }

    /**
     * Lets the owner add and remove away periods from now on; until then the card cannot be used.
     *
     * @param addedHandler gets a period the owner entered; called on the JavaFX thread
     * @param removedHandler gets a period the owner removed, as the model gave it; called on the JavaFX thread
     * @param clock tells the time when the owner saves a period, which must not have ended by then; never null
     */
    public void onChange(Consumer<OneOffAwayPeriod> addedHandler, Consumer<OneOffAwayPeriod> removedHandler,
            InstantSource clock) {
        added = addedHandler;
        removed = removedHandler;
        this.clock = clock;
        acceptsChanges = true;
    }

    /**
     * Whether the owner can add and remove periods here, so the card is worth showing.
     *
     * @return true once {@link #onChange} gave somewhere to send them
     */
    public boolean acceptsChanges() {
        return acceptsChanges;
    }

    /**
     * The entry, for the tests.
     *
     * @return the card's entry; shown only while the owner enters a period
     */
    OneOffAwayEditor editor() {
        return editor;
    }

    private HBox row(OneOffAwayModel.Listed entry) {
        var text = paragraph(entry.text(), "away-item");
        HBox.setHgrow(text, Priority.ALWAYS);
        text.setMaxWidth(Double.MAX_VALUE);
        var remove = new Button(messages.text(TextKey.ONE_OFF_REMOVE));
        remove.getStyleClass().addAll("flat", "small");
        remove.setMinWidth(USE_PREF_SIZE);
        remove.setAccessibleText(entry.removeSpoken());
        remove.setOnAction(_ -> removed.accept(entry.period()));
        var row = new HBox(text, remove);
        row.getStyleClass().add("away-row");
        return row;
    }

    /** After a save or a cancel the focus goes back to Add, where the keyboard was before the entry opened. */
    private void closeEntry() {
        closeEditor();
        add.requestFocus();
        resized.run();
    }

    /** Opens the entry on the selected day when the card accepts changes; otherwise it stays closed. */
    private void openEditor() {
        if (day != null && acceptsChanges()) {
            showEditor(true);
            editor.prepare(day.date(), model.zone(), clock);
        } else {
            closeEditor();
        }
    }

    private void closeEditor() {
        showEditor(false);
    }

    /** The entry and Add take turns: Add shows while the entry is closed. */
    private void showEditor(boolean shown) {
        editor.setVisible(shown);
        editor.setManaged(shown);
        add.setVisible(!shown);
        add.setManaged(!shown);
    }
}
