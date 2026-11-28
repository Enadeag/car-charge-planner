package nl.enadeag.carchargeplanner.desktop.settings.away;

import nl.enadeag.carchargeplanner.desktop.settings.field.ChoiceInput;
import nl.enadeag.carchargeplanner.desktop.settings.field.Input;
import nl.enadeag.carchargeplanner.desktop.settings.field.TimeInput;
import nl.enadeag.carchargeplanner.desktop.settings.form.ChoiceNames;
import nl.enadeag.carchargeplanner.desktop.settings.form.WeeklyAwayEntry;
import nl.enadeag.carchargeplanner.desktop.settings.page.ConfigPart;
import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.desktop.text.TextKey;
import nl.enadeag.carchargeplanner.desktop.theme.Labels;
import nl.enadeag.carchargeplanner.engine.away.WeeklyAwayPeriod;
import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

final class AwayRow extends VBox {

    private final Messages messages;
    private final ChoiceInput<DayOfWeek> weekday;
    private final TimeInput start;
    private final TimeInput end;
    private final Button removeButton;
    private final Label problem = Labels.paragraph("", "field-problem");

    AwayRow(WeeklyAwayPeriod period, Consumer<AwayRow> remove, Messages messages) {
        this.messages = messages;
        start = new TimeInput(messages);
        end = new TimeInput(messages);
        weekday = new ChoiceInput<>(List.of(DayOfWeek.values()), new ChoiceNames(messages)::weekday);
        weekday.load(period.weekday());
        start.load(period.start());
        end.load(period.end());
        removeButton = new Button(messages.text(TextKey.AWAY_REMOVE));
        removeButton.setOnAction(_ -> remove.accept(this));
        var from = new Label(messages.text(TextKey.AWAY_FROM));
        from.setLabelFor(start.focusable());
        var to = new Label(messages.text(TextKey.AWAY_TO));
        to.setLabelFor(end.focusable());
        // a row that does not fit, such as with the longer times of a 12-hour clock, wraps rather than cuts its words
        var line = new FlowPane(weekday.view(), from, start.view(), to, end.view(), removeButton);
        line.getStyleClass().add("away-line");
        problem.setVisible(false);
        problem.setManaged(false);
        getChildren().addAll(line, problem);
        getStyleClass().add("away-row");
    }

    /**
     * The period as the row shows it, after its record checked it; only reads, as {@link ConfigPart#read} does.
     *
     * @return the period, or empty when a time is not a time or the end is not after the start
     */
    Optional<WeeklyAwayPeriod> read() {
        return switch (checked()) {
            case WeeklyAwayEntry.Checked.Accepted accepted -> Optional.of(accepted.period());
            case WeeklyAwayEntry.Checked.Refused _ -> Optional.empty();
        };
    }

    /**
     * Shows the period's problem below it when it is refused, and takes a problem shown before away otherwise.
     */
    boolean showProblems() {
        return switch (checked()) {
            case WeeklyAwayEntry.Checked.Accepted _ -> {
                showProblem("");
                yield false;
            }
            case WeeklyAwayEntry.Checked.Refused refused -> {
                showProblem(refused.problem());
                yield true;
            }
        };
    }

    private WeeklyAwayEntry.Checked checked() {
        return new WeeklyAwayEntry(weekday.value(), start.text(), end.text()).check(messages);
    }

    boolean isChanged() {
        return weekday.isChanged() || start.isChanged() || end.isChanged();
    }

    /**
     * @param action what to run, on the JavaFX thread
     */
    void onChange(Runnable action) {
        List.<Input>of(weekday, start, end).forEach(input -> input.onChange(action));
    }

    private void showProblem(String text) {
        problem.setText(text);
        problem.setVisible(!text.isEmpty());
        problem.setManaged(!text.isEmpty());
    }
}
