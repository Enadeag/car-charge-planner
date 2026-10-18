package io.github.enadeag.carchargeplanner.desktop.settings;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import javafx.scene.Node;
import javafx.scene.control.TextField;

/** A time of day as hours and minutes, such as 07:30. Both of the app's languages write it on a 24-hour clock. */
final class TimeInput implements Input {

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)
            .withResolverStyle(ResolverStyle.STRICT);

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
        field.setText(CLOCK.format(time));
    }

    /**
     * The time as typed.
     *
     * @return the time, or empty when the text is not a time such as 07:30
     */
    Optional<LocalTime> value() {
        try {
            return Optional.of(LocalTime.parse(field.getText().strip(), CLOCK));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
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
        return !Objects.equals(value().orElse(null), loaded);
    }

    @Override
    public void onChange(Runnable action) {
        field.textProperty().addListener((property, before, now) -> action.run());
    }
}
