package io.github.enadeag.carchargeplanner.desktop;

import io.github.enadeag.carchargeplanner.engine.away.AwayPeriod;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * Where the owner enters an away period on one day: the whole day, or part of it from a time to a time, with Save and
 * Cancel. A period that is not valid is not saved: the entry says why and stays open.
 */
final class AwayEditor extends VBox {

    /** Both of the app's languages write a time on a 24-hour clock; a leading zero may be left out. */
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("H:mm")
            .withResolverStyle(ResolverStyle.STRICT);
    private static final LocalTime FIRST_START = LocalTime.of(9, 0);
    private static final LocalTime FIRST_END = LocalTime.of(17, 0);

    private final Messages messages;
    private final Consumer<AwayPeriod> saved;
    private final Runnable closed;
    private final RadioButton wholeDay;
    private final RadioButton partOfDay;
    private final TextField from = new TextField();
    private final TextField to = new TextField();
    private final HBox times;
    private final Label problem = Texts.paragraph("", "field-problem");
    private LocalDate day = LocalDate.EPOCH;
    private ZoneId zone = ZoneId.of("UTC");
    private Instant now = Instant.EPOCH;

    /**
     * Makes the entry, closed until {@link #start}.
     *
     * @param saved gets the period the owner saved; called on the JavaFX thread
     * @param closed called when the entry should close, after a save or a cancel
     * @param messages the texts, in the owner's language
     */
    AwayEditor(Consumer<AwayPeriod> saved, Runnable closed, Messages messages) {
        this.messages = messages;
        this.saved = saved;
        this.closed = closed;
        var kind = new ToggleGroup();
        wholeDay = new RadioButton(messages.text(TextKey.ONE_OFF_WHOLE_DAY));
        partOfDay = new RadioButton(messages.text(TextKey.ONE_OFF_PART_OF_DAY));
        wholeDay.setToggleGroup(kind);
        partOfDay.setToggleGroup(kind);
        kind.selectedToggleProperty().addListener((property, before, chosen) -> showTimes());
        var kinds = new FlowPane(wholeDay, partOfDay);
        kinds.getStyleClass().add("away-kinds");

        from.setAccessibleText(messages.text(TextKey.AWAY_FROM_FIELD));
        to.setAccessibleText(messages.text(TextKey.AWAY_TO_FIELD));
        for (var field : List.of(from, to)) {
            field.getStyleClass().add("away-time");
            field.setPrefColumnCount(4);
            field.setOnAction(event -> save());
        }
        times = new HBox(Texts.label(messages.text(TextKey.AWAY_FROM), "fact"), from,
                Texts.label(messages.text(TextKey.AWAY_TO), "fact"), to);
        times.getStyleClass().add("away-times");

        var save = new Button(messages.text(TextKey.ONE_OFF_SAVE));
        save.getStyleClass().add("accent");
        save.setOnAction(event -> save());
        var cancel = new Button(messages.text(TextKey.ONE_OFF_CANCEL));
        cancel.getStyleClass().add("flat");
        cancel.setOnAction(event -> closed.run());
        var actions = new FlowPane(times, save, cancel);
        actions.getStyleClass().add("away-actions");

        setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                closed.run();
                event.consume();
            }
        });
        getChildren().setAll(kinds, actions, problem);
        getStyleClass().add("away-editor");
        showProblem("");
    }

    /**
     * Opens the entry for a day, at the whole day, and puts the focus in it.
     *
     * @param day the day the car is away
     * @param zone the time zone of the owner's home, in which the times are read
     * @param now the current moment; a period that ends by then is refused
     */
    void start(LocalDate day, ZoneId zone, Instant now) {
        this.day = day;
        this.zone = zone;
        this.now = now;
        wholeDay.setSelected(true);
        from.setText(CLOCK.format(FIRST_START));
        to.setText(CLOCK.format(FIRST_END));
        showProblem("");
        wholeDay.requestFocus();
    }

    /** Saves the period as entered, or says why it cannot be saved. */
    void save() {
        period().ifPresent(period -> {
            saved.accept(period);
            closed.run();
        });
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

    private Optional<AwayPeriod> period() {
        if (wholeDay.isSelected()) {
            return notPassed(AwayPeriod.allDay(day, zone));
        }
        var start = time(from);
        var end = time(to);
        if (start.isEmpty() || end.isEmpty()) {
            return refuse(TextKey.PROBLEM_NOT_A_TIME);
        }
        try {
            return notPassed(AwayPeriod.on(day, start.get(), end.get(), zone));
        } catch (IllegalArgumentException e) {
            // also when the clock is put forward between the two times, so both are the same moment
            return refuse(TextKey.ONE_OFF_END_BEFORE_START);
        }
    }

    private Optional<AwayPeriod> notPassed(AwayPeriod period) {
        if (!period.end().isAfter(now)) {
            return refuse(TextKey.ONE_OFF_PASSED);
        }
        showProblem("");
        return Optional.of(period);
    }

    private Optional<AwayPeriod> refuse(TextKey why) {
        showProblem(messages.text(why));
        return Optional.empty();
    }

    private static Optional<LocalTime> time(TextField field) {
        try {
            return Optional.of(LocalTime.parse(field.getText().strip(), CLOCK));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    private void showTimes() {
        boolean part = partOfDay.isSelected();
        times.setVisible(part);
        times.setManaged(part);
    }

    private void showProblem(String text) {
        problem.setText(text);
        problem.setVisible(!text.isEmpty());
        problem.setManaged(!text.isEmpty());
        List.of(from, to, wholeDay, partOfDay).forEach(node -> node.setAccessibleHelp(text));
    }
}
