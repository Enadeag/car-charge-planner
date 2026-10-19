package nl.enadeag.carchargeplanner.desktop.dashboard.solar;

import static nl.enadeag.carchargeplanner.desktop.theme.Labels.line;

import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.desktop.text.TextKey;
import javafx.beans.value.ObservableValue;
import javafx.scene.Node;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;

/** The card with the selected day's solar chart, its heading and a legend of what each colour and line is. */
public final class SolarCard extends VBox {

    private static final double SWATCH = 10;

    private final SolarChart chart;

    /**
     * Makes the card, empty until {@link #show(SolarCurveModel)}.
     *
     * @param reducedMotion true while the system asks for reduced motion; the chart then changes without moving
     * @param messages the texts, in the owner's language
     */
    public SolarCard(ObservableValue<Boolean> reducedMotion, Messages messages) {
        chart = new SolarChart(reducedMotion, messages.text(TextKey.CHART_NOW), messages.text(TextKey.CHART_KEYS));
        var spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        var header = new HBox(line(messages.text(TextKey.CHART_SOLAR), "card-label"), spacer,
                entry(area(), messages.text(TextKey.CHART_SOLAR_LEGEND)),
                entry(houseLine(), messages.text(TextKey.CHART_HOUSE_LEGEND)),
                entry(window(), messages.text(TextKey.CHART_WINDOW_LEGEND)));
        header.getStyleClass().add("chart-header");
        getChildren().setAll(header, chart);
        getStyleClass().addAll("card", "solar-card");
    }

    /**
     * Shows a day's chart.
     *
     * @param day the day's data; never null
     */
    public void show(SolarCurveModel day) {
        chart.show(day);
    }

    /**
     * The chart itself, for the tests.
     *
     * @return the card's chart
     */
    SolarChart chart() {
        return chart;
    }

    private static HBox entry(Node swatch, String text) {
        var entry = new HBox(swatch, line(text, "legend-text"));
        entry.getStyleClass().add("legend-entry");
        return entry;
    }

    private static Node area() {
        var swatch = new Rectangle(SWATCH, SWATCH);
        swatch.getStyleClass().addAll("legend-sun", "chart-fill");
        return swatch;
    }

    private static Node houseLine() {
        var swatch = new Line(0, 0, SWATCH + 4, 0);
        swatch.getStyleClass().addAll("house-line", "chart-stroke");
        return swatch;
    }

    private static Node window() {
        var swatch = new Rectangle(SWATCH, SWATCH);
        swatch.getStyleClass().addAll("legend-window", "chart-fill");
        return swatch;
    }
}
