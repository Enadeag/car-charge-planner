package io.github.enadeag.carchargeplanner.desktop.settings;

import io.github.enadeag.carchargeplanner.desktop.text.Messages;
import io.github.enadeag.carchargeplanner.desktop.text.TextKey;
import io.github.enadeag.carchargeplanner.desktop.theme.ScreenReader;
import io.github.enadeag.carchargeplanner.engine.away.RecurringAwayPeriod;
import io.github.enadeag.carchargeplanner.engine.setting.InvalidSetting;
import java.time.DayOfWeek;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/** One weekly away period: a weekday, from a time to a time, and a way to remove it. */
final class AwayRow extends VBox {

    private final Messages messages;
    private final ChoiceField<DayOfWeek> weekday;
    private final TimeInput start = new TimeInput();
    private final TimeInput end = new TimeInput();
    private final Button removeButton;
    private final Label problem = FieldRow.paragraph("", "field-problem");

    /**
     * Makes the row.
     *
     * @param period the period to show, as the one not edited
     * @param remove what the row's Remove button does; given this row
     * @param messages the texts and day names, in the owner's language
     */
    AwayRow(RecurringAwayPeriod period, Consumer<AwayRow> remove, Messages messages) {
        this.messages = messages;
        weekday = new ChoiceField<>(List.of(DayOfWeek.values()), day -> capitalised(day, messages));
        weekday.load(period.weekday());
        start.load(period.start());
        end.load(period.end());
        removeButton = new Button(messages.text(TextKey.AWAY_REMOVE));
        removeButton.setOnAction(event -> remove.accept(this));
        var line = new HBox(weekday.view(), new Label(messages.text(TextKey.AWAY_FROM)), start.view(),
                new Label(messages.text(TextKey.AWAY_TO)), end.view(), removeButton);
        line.getStyleClass().add("away-line");
        problem.setVisible(false);
        problem.setManaged(false);
        getChildren().addAll(line, problem);
        getStyleClass().add("away-row");
        number(0, 1);
    }

    /**
     * Gives the row its place among the weekly away periods: with more than one, a screen reader reads each field
     * and the Remove button with the period's number, so the rows' twins differ.
     *
     * @param index the row's place, from 0
     * @param count how many rows there are, at least 1
     */
    void number(int index, int count) {
        var period = messages.text(TextKey.AWAY_PERIOD_NUMBER, index + 1);
        ScreenReader.name(weekday.focusable(), spoken(TextKey.AWAY_WEEKDAY, period, count));
        ScreenReader.name(start.focusable(), spoken(TextKey.AWAY_FROM_FIELD, period, count));
        ScreenReader.name(end.focusable(), spoken(TextKey.AWAY_TO_FIELD, period, count));
        // the Remove button's text is short; its name always says which period it removes
        removeButton.setAccessibleText(messages.text(TextKey.IN_GROUP, removeButton.getText(), period));
    }

    private String spoken(TextKey name, String period, int count) {
        return count > 1 ? messages.text(TextKey.IN_GROUP, messages.text(name), period) : messages.text(name);
    }

    /**
     * The period as the row shows it, after its record checked it; a refused one shows its problem.
     *
     * @return the period, or empty when a time is not a time or the end is not after the start
     */
    Optional<RecurringAwayPeriod> value() {
        showProblem("");
        var from = start.value();
        var to = end.value();
        if (from.isEmpty() || to.isEmpty()) {
            showProblem(messages.text(TextKey.PROBLEM_NOT_A_TIME));
            return Optional.empty();
        }
        try {
            return Optional.of(new RecurringAwayPeriod(weekday.value(), from.get(), to.get()));
        } catch (InvalidSetting e) {
            showProblem(ProblemTexts.of(e.rule(), messages));
            return Optional.empty();
        }
    }

    /**
     * Whether the owner changed the period since it was shown first.
     *
     * @return true when the weekday or a time differs
     */
    boolean isChanged() {
        return weekday.isChanged() || start.isChanged() || end.isChanged();
    }

    /**
     * Calls an action whenever the period changes.
     *
     * @param action what to run, on the JavaFX thread
     */
    void onChange(Runnable action) {
        List.<Input>of(weekday, start, end).forEach(input -> input.onChange(action));
    }

    private void showProblem(String text) {
        problem.setText(text);
        problem.setVisible(!text.isEmpty());
        problem.setManaged(!text.isEmpty());
        List.of(weekday.focusable(), start.focusable(), end.focusable()).forEach(node -> node.setAccessibleHelp(text));
    }

    private static String capitalised(DayOfWeek day, Messages messages) {
        var name = day.getDisplayName(TextStyle.FULL, messages.language().locale());
        return name.substring(0, 1).toUpperCase(messages.language().locale()) + name.substring(1);
    }
}
