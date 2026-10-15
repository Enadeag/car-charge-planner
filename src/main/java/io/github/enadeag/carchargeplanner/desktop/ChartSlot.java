package io.github.enadeag.carchargeplanner.desktop;

import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;

/** The place the day's solar curve and battery charts will take; until they are built, it says so. */
final class ChartSlot extends StackPane {

    /** Makes the slot with its "not available yet" text. */
    ChartSlot() {
        getChildren().setAll(new Label("Solar curve and battery: not available yet"));
        getStyleClass().add("chart-slot");
    }
}
