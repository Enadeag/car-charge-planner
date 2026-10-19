package nl.enadeag.carchargeplanner.desktop.settings;

import nl.enadeag.carchargeplanner.desktop.settings.form.TimeText;
import java.time.LocalTime;
import java.util.Objects;
import javafx.scene.Node;
import javafx.scene.control.TextField;

/** A time of day as hours and minutes, such as 07:30, written and read by {@link TimeText}. */
final class TimeInput implements Input {

    private final TextField field = new TextField();
    private LocalTime loaded = LocalTime.MIDNIGHT;

    /** Makes the field, at 00:00 until {@link #load(LocalTime)}. */
    TimeInput() {
        field.getStyleClass().add("time-input");
        field.setPrefColumnCount(5);
        load(loaded);
    }

    /**
     * Shows a time and remembers it as the one not edited.
     *
     * @param time the time to show, to the minute
     */
    void load(LocalTime time) {
        loaded = time;
        field.setText(TimeText.write(time));
    }

    /**
     * The time as typed.
     *
     * @return what the field holds, such as "07:30"; not necessarily a time
     */
    String text() {
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
        return !Objects.equals(TimeText.read(text()).orElse(null), loaded);
    }

    @Override
    public void onChange(Runnable action) {
        field.textProperty().addListener((property, before, now) -> action.run());
    }
}
