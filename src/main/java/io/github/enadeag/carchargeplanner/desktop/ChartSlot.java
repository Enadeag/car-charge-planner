package io.github.enadeag.carchargeplanner.desktop;

import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;

/** The place the day's solar curve and battery charts will take; until they are built, it says so. */
final class ChartSlot extends StackPane {

    /**
     * Makes the slot with its "not available yet" text.
     *
     * @param messages the texts, in the owner's language
     */
    ChartSlot(Messages messages) {
        getChildren().setAll(new Label(messages.text(TextKey.CHART_NOT_YET)));
        getStyleClass().add("chart-slot");
    }
}
