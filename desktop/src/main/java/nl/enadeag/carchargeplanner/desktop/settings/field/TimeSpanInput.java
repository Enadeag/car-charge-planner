package nl.enadeag.carchargeplanner.desktop.settings.field;

import nl.enadeag.carchargeplanner.desktop.text.Messages;
import java.time.LocalTime;
import java.util.List;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;

/**
 * Two times of day with a word between them, such as 07:00 and 22:00. The field's row names the first; the word
 * between them is the second's label.
 */
public final class TimeSpanInput implements Input {

    private final TimeInput start;
    private final TimeInput end;
    private final HBox view;

    /**
     * Makes the field, at 00:00 and 00:00 until {@link #load(LocalTime, LocalTime)}.
     *
     * @param between the word between the times, such as "and", in the owner's language
     * @param messages the owner's language and the system's clock, which the times are shown on
     */
    public TimeSpanInput(String between, Messages messages) {
        start = new TimeInput(messages);
        end = new TimeInput(messages);
        var word = new Label(between);
        word.setLabelFor(end.focusable());
        word.getStyleClass().add("time-span-word");
        view = new HBox(start.view(), word, end.view());
        view.getStyleClass().add("time-span-input");
        if (messages.twelveHour()) {
            view.getStyleClass().add("twelve-hour");
        }
    }

    /**
     * Shows two times and remembers them as the ones not edited.
     *
     * @param first the first time, to the minute
     * @param second the second time, to the minute
     */
    public void load(LocalTime first, LocalTime second) {
        start.load(first);
        end.load(second);
    }

    /**
     * The first time as typed.
     *
     * @return what the first field holds, such as "07:00"; not necessarily a time
     */
    public String startText() {
        return start.text();
    }

    /**
     * The second time as typed.
     *
     * @return what the second field holds, such as "22:00"; not necessarily a time
     */
    public String endText() {
        return end.text();
    }

    @Override
    public Node view() {
        return view;
    }

    @Override
    public Node focusable() {
        return start.focusable();
    }

    @Override
    public boolean isChanged() {
        return start.isChanged() || end.isChanged();
    }

    @Override
    public void onChange(Runnable action) {
        List.of(start, end).forEach(time -> time.onChange(action));
    }
}
