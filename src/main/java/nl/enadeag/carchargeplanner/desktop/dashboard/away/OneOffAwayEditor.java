package nl.enadeag.carchargeplanner.desktop.dashboard.away;

import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.desktop.text.TextKey;
import nl.enadeag.carchargeplanner.desktop.theme.ScreenReader;
import nl.enadeag.carchargeplanner.desktop.theme.Labels;
import nl.enadeag.carchargeplanner.engine.away.OneOffAwayPeriod;
import java.time.Instant;
import java.time.InstantSource;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * Where the owner enters an away period on one day: the whole day, or part of it from a time to a time, with Save and
 * Cancel; Enter saves and Escape cancels. A period that is not valid is not saved: the entry says why, stays open and
 * puts the focus where the owner can fix it, where a screen reader reads the reason.
 */
final class OneOffAwayEditor extends VBox {

    private static final DateTimeFormatter SHOWN = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);
    private static final LocalTime FIRST_START = LocalTime.of(9, 0);
    private static final LocalTime FIRST_END = LocalTime.of(17, 0);

    private final Messages messages;
    private final Consumer<OneOffAwayPeriod> saved;
    private final Runnable closed;
    private final Runnable resized;
    private final RadioButton wholeDay;
    private final RadioButton partOfDay;
    private final TextField from = new TextField();
    private final TextField to = new TextField();
    private final HBox times;
    private final Label problem = Labels.paragraph("", "field-problem");
    private OneOffAwayCheck check = new OneOffAwayCheck(LocalDate.EPOCH, ZoneId.of("UTC"),
            InstantSource.fixed(Instant.EPOCH));

    /**
     * Makes the entry, empty until {@link #prepare}.
     *
     * @param saved gets the period the owner saved; called on the JavaFX thread
     * @param closed called when the entry should close, after a save or a cancel
     * @param resized called when the entry grows or shrinks while open: the times or a problem show or go
     * @param messages the texts, in the owner's language
     */
    OneOffAwayEditor(Consumer<OneOffAwayPeriod> saved, Runnable closed, Runnable resized, Messages messages) {
        this.messages = messages;
        this.saved = saved;
        this.closed = closed;
        this.resized = resized;
        var kind = new ToggleGroup();
        wholeDay = new RadioButton(messages.text(TextKey.ONE_OFF_WHOLE_DAY));
        partOfDay = new RadioButton(messages.text(TextKey.ONE_OFF_PART_OF_DAY));
        wholeDay.setToggleGroup(kind);
        partOfDay.setToggleGroup(kind);
        kind.selectedToggleProperty().addListener((_, _, _) -> syncTimesToChoice());
        var kinds = new FlowPane(wholeDay, partOfDay);
        kinds.getStyleClass().add("away-kinds");

        ScreenReader.name(from, messages.text(TextKey.AWAY_FROM_FIELD));
        ScreenReader.name(to, messages.text(TextKey.AWAY_TO_FIELD));
        for (var field : List.of(from, to)) {
            field.getStyleClass().add("away-time");
            field.setPrefColumnCount(4);
        }
        times = new HBox(Labels.line(messages.text(TextKey.AWAY_FROM), "fact"), from,
                Labels.line(messages.text(TextKey.AWAY_TO), "fact"), to);
        times.getStyleClass().add("away-times");

        var save = new Button(messages.text(TextKey.ONE_OFF_SAVE));
        save.getStyleClass().add("accent");
        save.setOnAction(_ -> save());
        var cancel = new Button(messages.text(TextKey.ONE_OFF_CANCEL));
        cancel.getStyleClass().add("flat");
        cancel.setOnAction(_ -> closed.run());
        // the battery's entry above can be open as well, with its own Save and Cancel
        var heading = messages.text(TextKey.ONE_OFF_HEADING);
        save.setAccessibleText(messages.text(TextKey.IN_GROUP, save.getText(), heading));
        cancel.setAccessibleText(messages.text(TextKey.IN_GROUP, cancel.getText(), heading));
        var buttons = new HBox(save, cancel);
        buttons.getStyleClass().add("away-buttons");
        var actions = new FlowPane(times, buttons);
        actions.getStyleClass().add("away-actions");

        setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                closed.run();
                event.consume();
            }
        });
        // Enter saves from the times and from the choice of whole or part of the day, before a radio button takes it
        // to choose itself; Save and Cancel take Enter themselves
        addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ENTER && !(event.getTarget() instanceof Button)) {
                save();
                event.consume();
            }
        });
        getChildren().setAll(kinds, actions, problem);
        getStyleClass().add("away-editor");
        problem.setVisible(false);
        problem.setManaged(false);
    }

    /**
     * Fills the entry for a day, at the whole day, and puts the focus in it; the card shows the entry.
     *
     * @param day the day the car is away
     * @param zone the time zone of the owner's home, in which the times are read
     * @param clock tells the time when the owner saves: a period that has ended by then is refused, also when the
     *     plan shown was made earlier
     */
    void prepare(LocalDate day, ZoneId zone, InstantSource clock) {
        check = new OneOffAwayCheck(day, zone, clock);
        wholeDay.setSelected(true);
        from.setText(SHOWN.format(FIRST_START));
        to.setText(SHOWN.format(FIRST_END));
        showProblem("");
        wholeDay.requestFocus();
    }

    /** Saves the period as entered, or says why it cannot be saved. */
    void save() {
        switch (checked()) {
            case OneOffAwayCheck.Checked.Valid(var period) -> {
                showProblem("");
                saved.accept(period);
                closed.run();
            }
            case OneOffAwayCheck.Checked.Refused(var why) -> {
                showProblem(messages.text(why));
                ScreenReader.focus(partOfDay.isSelected() ? from : wholeDay);
            }
        }
    }

    /**
     * Chooses part of the day and its times, as the owner does by hand.
     *
     * @param start the start as typed, such as "10:00"
     * @param end the end as typed, such as "16:30"
     */
    void enterTimes(String start, String end) {
        partOfDay.setSelected(true);
        from.setText(start);
        to.setText(end);
    }

    /**
     * Why the entered period was not saved.
     *
     * @return the problem shown, or empty while there is none
     */
    String problem() {
        return problem.isVisible() ? problem.getText() : "";
    }

    /** The period as entered, or why it cannot be saved; changes nothing on screen. */
    private OneOffAwayCheck.Checked checked() {
        return wholeDay.isSelected() ? check.wholeDay() : check.partOfDay(from.getText(), to.getText());
    }

    private void syncTimesToChoice() {
        boolean part = partOfDay.isSelected();
        times.setVisible(part);
        times.setManaged(part);
        resized.run();
    }

    private void showProblem(String text) {
        problem.setText(text);
        problem.setVisible(!text.isEmpty());
        problem.setManaged(!text.isEmpty());
        List.of(from, to, wholeDay, partOfDay).forEach(node -> node.setAccessibleHelp(text));
        resized.run();
    }
}
