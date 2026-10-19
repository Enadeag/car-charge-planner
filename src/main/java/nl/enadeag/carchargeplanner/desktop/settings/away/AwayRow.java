package nl.enadeag.carchargeplanner.desktop.settings.away;

import nl.enadeag.carchargeplanner.desktop.settings.field.ChoiceField;
import nl.enadeag.carchargeplanner.desktop.settings.field.Input;
import nl.enadeag.carchargeplanner.desktop.settings.field.TimeInput;
import nl.enadeag.carchargeplanner.desktop.settings.form.AwayPeriodEntry;
import nl.enadeag.carchargeplanner.desktop.settings.form.ChoiceNames;
import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.desktop.text.TextKey;
import nl.enadeag.carchargeplanner.desktop.theme.ScreenReader;
import nl.enadeag.carchargeplanner.desktop.theme.Labels;
import nl.enadeag.carchargeplanner.engine.away.WeeklyAwayPeriod;
import java.time.DayOfWeek;
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
    private final Label problem = Labels.paragraph("", "field-problem");

    /**
     * Makes the row.
     *
     * @param period the period to show, as the one not edited
     * @param remove what the row's Remove button does; given this row
     * @param messages the texts and day names, in the owner's language
     */
    AwayRow(WeeklyAwayPeriod period, Consumer<AwayRow> remove, Messages messages) {
        this.messages = messages;
        weekday = new ChoiceField<>(List.of(DayOfWeek.values()), new ChoiceNames(messages)::weekday);
        weekday.load(period.weekday());
        start.load(period.start());
        end.load(period.end());
        removeButton = new Button(messages.text(TextKey.AWAY_REMOVE));
        removeButton.setOnAction(_ -> remove.accept(this));
        var line = new HBox(weekday.view(), new Label(messages.text(TextKey.AWAY_FROM)), start.view(),
                new Label(messages.text(TextKey.AWAY_TO)), end.view(), removeButton);
        line.getStyleClass().add("away-line");
        problem.setVisible(false);
        problem.setManaged(false);
        getChildren().addAll(line, problem);
        getStyleClass().add("away-row");
        placeAmong(0, 1);
    }

    /**
     * Gives the row its place among the weekly away periods: with more than one, a screen reader reads each field
     * and the Remove button with the period's number, so the rows' twins differ.
     *
     * @param index the row's place, from 0
     * @param count how many rows there are, at least 1
     */
    void placeAmong(int index, int count) {
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
     * The period as the row shows it, after its record checked it; only reads, as
     * {@link nl.enadeag.carchargeplanner.desktop.settings.page.ConfigPart#read} does.
     *
     * @return the period, or empty when a time is not a time or the end is not after the start
     */
    Optional<WeeklyAwayPeriod> read() {
        return switch (checked()) {
            case AwayPeriodEntry.Checked.Accepted accepted -> Optional.of(accepted.period());
            case AwayPeriodEntry.Checked.Refused _ -> Optional.empty();
        };
    }

    /**
     * Shows the period's problem below it when it is refused, and takes a problem shown before away otherwise.
     *
     * @return true when it is refused
     */
    boolean showProblems() {
        return switch (checked()) {
            case AwayPeriodEntry.Checked.Accepted _ -> {
                showProblem("");
                yield false;
            }
            case AwayPeriodEntry.Checked.Refused refused -> {
                showProblem(refused.problem());
                yield true;
            }
        };
    }

    private AwayPeriodEntry.Checked checked() {
        return new AwayPeriodEntry(weekday.value(), start.text(), end.text()).check(messages);
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
}
