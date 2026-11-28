package nl.enadeag.carchargeplanner.desktop.settings.field;

import nl.enadeag.carchargeplanner.desktop.settings.form.TimeText;
import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.desktop.theme.TimeFields;
import java.time.LocalTime;
import java.util.Objects;
import javafx.scene.Node;
import javafx.scene.control.TextField;

/**
 * A time of day as hours and minutes, such as 07:30 or 7:30 am on the system's clock, written and read by
 * {@link TimeText}.
 */
public final class TimeInput implements Input {

    private final TextField field = new TextField();
    private final Messages messages;
    private LocalTime loaded = LocalTime.MIDNIGHT;

    /**
     * Makes the field, at midnight until {@link #load(LocalTime)}.
     *
     * @param messages the owner's language and the system's clock, which the time is shown on
     */
    public TimeInput(Messages messages) {
        this.messages = messages;
        field.getStyleClass().add("time-input");
        TimeFields.fit(field, messages);
        load(loaded);
    }

    /**
     * Shows a time and remembers it as the one not edited.
     *
     * @param time the time to show, to the minute
     */
    public void load(LocalTime time) {
        loaded = time;
        field.setText(TimeText.write(time, messages));
    }

    /**
     * The time as typed.
     *
     * @return what the field holds, such as "07:30" or "7:30 am"; not necessarily a time
     */
    public String text() {
        return field.getText();
    }

    @Override
    public Node view() {
        return field;
    }

    @Override
    public Node focusable() {
        return field;
    }

    @Override
    public boolean isChanged() {
        return !Objects.equals(TimeText.read(text(), messages).orElse(null), loaded);
    }

    @Override
    public void onChange(Runnable action) {
        field.textProperty().addListener((_, _, _) -> action.run());
    }
}
