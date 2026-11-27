package nl.enadeag.carchargeplanner.desktop.theme;

import nl.enadeag.carchargeplanner.desktop.text.Messages;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;
import javafx.beans.binding.Bindings;
import javafx.scene.control.TextField;
import javafx.scene.text.Font;
import javafx.scene.text.Text;

/**
 * Text fields that hold a time of day, as wide as the widest time on the owner's clock in the field's own font, so
 * no font or text size makes a time scroll out of sight.
 */
public final class TimeFields {

    private static final int MINUTES_A_DAY = 24 * 60;
    /** Room for the caret after the last character, as a share of the font's size. */
    private static final double CARET_ROOM = 0.25;
    private static final Map<Widest, Double> WIDTHS = new ConcurrentHashMap<>();

    private record Widest(List<String> times, Font font) {
    }

    private TimeFields() {
    }

    /**
     * Keeps the field as wide as the widest time of day in its language and on its clock, measured in the font the
     * field has now, so it follows the text size; a stylesheet must not set the field's width.
     *
     * @param field the field that holds a time, such as "07:30" or "7:30 p.m."
     * @param messages the owner's language and the system's clock, which the times are written in
     */
    public static void fit(TextField field, Messages messages) {
        var times = IntStream.range(0, MINUTES_A_DAY)
                .mapToObj(minute -> messages.time(LocalTime.MIDNIGHT.plusMinutes(minute)))
                .distinct().toList();
        var width = Bindings.createDoubleBinding(() -> {
            var font = field.getFont();
            return Math.ceil(WIDTHS.computeIfAbsent(new Widest(times, font), TimeFields::measure)
                    + font.getSize() * CARET_ROOM + field.getInsets().getLeft() + field.getInsets().getRight());
        }, field.fontProperty(), field.insetsProperty());
        field.minWidthProperty().bind(width);
        field.prefWidthProperty().bind(width);
    }

    private static double measure(Widest widest) {
        var text = new Text();
        text.setFont(widest.font());
        return widest.times().stream().mapToDouble(time -> {
            text.setText(time);
            return text.getLayoutBounds().getWidth();
        }).max().orElse(0);
    }
}
