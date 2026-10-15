package io.github.enadeag.carchargeplanner.desktop;

import atlantafx.base.theme.Styles;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

/** The forecast's source and freshness, and the way to Away and Settings, which are not built yet. */
final class DashboardFooter extends HBox {

    private final Label forecast = Texts.paragraph("", "footer-text");

    /** Makes the footer, with an empty forecast text and the Away and Settings buttons switched off. */
    DashboardFooter() {
        HBox.setHgrow(forecast, Priority.ALWAYS);
        forecast.setMaxWidth(Double.MAX_VALUE);
        getChildren().setAll(forecast, notYet("Away"), notYet("Settings"));
        getStyleClass().add("dashboard-footer");
    }

    /**
     * Shows the forecast's source and freshness.
     *
     * @param text such as "Forecast: Open-Meteo · 10:51"; never null
     */
    void show(String text) {
        forecast.setText(text);
    }

    private static Button notYet(String name) {
        var button = new Button(name + " · soon");
        button.getStyleClass().add(Styles.SMALL);
        button.setDisable(true);
        button.setMinWidth(USE_PREF_SIZE);
        button.setAccessibleText(name + ", not available yet");
        return button;
    }
}
